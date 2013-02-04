package net.pnyxter.actor.instrument;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import net.pnyxter.actor.Actor;
import net.pnyxter.actor.Inbox;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

public class ActorWeaver implements Opcodes {

	private static final String IN_ACTOR_PREFIX = "__in_actor__";

	private static final String ACTOR_DESC = Type.getDescriptor(Actor.class);
	private static final String INBOX_DESC = Type.getDescriptor(Inbox.class);
	private static final String FUTURE_DESC = Type.getDescriptor(Future.class);

	private static final AnnotationVisitor IGNORE_ANNOTATION = new AnnotationVisitor(ASM4) {
	};

	private static class CallerDescription extends SignatureVisitor {

		final String outerClassName;

		final String methodName;
		final String methodDesc;

		final String methodSignature;
		final String constructorSignature;

		final String source;
		final int line;

		final String className;

		boolean voidReturn = false;
		String futureDesc = null;

		String[] parameterDesc = new String[0];

		public CallerDescription(String outerClassName, String name, String description, String signature, String source, int line) throws IllegalInboxMethodException {
			super(ASM4);

			this.outerClassName = outerClassName;
			this.source = source;
			this.line = line;

			this.methodName = name;
			this.methodDesc = description;

			if (signature == null) {
				this.methodSignature = signature;
			} else {
				this.methodSignature = description;
			}

			new SignatureReader(methodSignature).accept(this);

			if (voidReturn) {
				this.constructorSignature = "(L" + outerClassName + ";" + methodSignature.substring(1);
			} else {
				int pos = methodSignature.indexOf(')');
				this.constructorSignature = "(L" + outerClassName + ";" + methodSignature.substring(1, pos + 1) + "V";
			}

			StringBuilder nameBuilder = new StringBuilder("Caller_");
			nameBuilder.append(name);

			for (String a : parameterDesc) {
				nameBuilder.append("_");
				if (a.endsWith(";")) {
					nameBuilder.append(a.substring(a.lastIndexOf('/') + 1, a.length() - 1).replace('$', '_'));
				} else {
					nameBuilder.append(a);
				}
			}

			className = nameBuilder.toString();
		}

		@Override
		public SignatureVisitor visitParameterType() {
			return new SignatureVisitor(ASM4) {
				String array = "";
				String desc = null;

				@Override
				public void visitBaseType(char descriptor) {
					if (desc == null) {
						desc = array + String.valueOf(descriptor);
					}
				}

				@Override
				public void visitClassType(String name) {
					if (desc == null) {
						desc = array + name + ";";
					}
				}

				@Override
				public SignatureVisitor visitArrayType() {
					if (desc == null) {
						array = "[";
					}
					return this;
				}

				@Override
				public void visitEnd() {
					if (desc == null) {
						throw new IllegalInboxMethodException("Unsupported parameter type in inbox method. name=" + methodName + " " + source + ":" + line);
					}
					parameterDesc = Arrays.copyOf(parameterDesc, parameterDesc.length + 1);
					parameterDesc[parameterDesc.length - 1] = desc;
				}
			};
		}

		@Override
		public SignatureVisitor visitReturnType() {
			return new SignatureVisitor(ASM4) {

				boolean foundFuture = false;

				@Override
				public void visitBaseType(char descriptor) {
					if ('V' == descriptor) {
						voidReturn = true;
					}
				}

				@Override
				public void visitClassType(String name) {
					if (foundFuture) {
						futureDesc = name + ";";
					} else if (FUTURE_DESC.equals(name + ";")) {
						foundFuture = true;
					}
				}

				@Override
				public void visitEnd() {
					if (foundFuture) {
						foundFuture = false;
					} else if (!voidReturn && futureDesc == null) {
						throw new IllegalInboxMethodException("Only void and Future<> is allowed return types on method annotated with @Inbox. " + source + ":" + line);
					}
				}
			};
		}

		public String getFullClassName() {
			return outerClassName + "$" + className;
		}

	}

	private static byte[] createInboxCallAction(CallerDescription caller) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		FieldVisitor fv;
		MethodVisitor mv;

		String innerClassName = caller.className;
		String fullInnerClassName = caller.getFullClassName();

		cw.visit(V1_7, ACC_FINAL + ACC_SUPER, fullInnerClassName, null, "net/pnyxter/actor/dispatcher/ActorQueue$Action", null);

		cw.visitSource(caller.source, null);

		cw.visitInnerClass(fullInnerClassName, caller.outerClassName, innerClassName, ACC_PRIVATE + ACC_FINAL);

		cw.visitInnerClass("net/pnyxter/actor/dispatcher/ActorQueue$Action", "net/pnyxter/actor/dispatcher/ActorQueue", "Action", ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

		StringBuilder params = new StringBuilder();
		int i = 0;

		for (String a : caller.parameterDesc) {
			i++;
			fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "a" + i, a, null, null);
			fv.visitEnd();

			params.append(a).append(";");
		}
		{
			fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$0", "L" + caller.outerClassName + ";", null, null);
			fv.visitEnd();
		}
		{
			mv = cw.visitMethod(0, "<init>", caller.constructorSignature, null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(caller.line, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitFieldInsn(PUTFIELD, fullInnerClassName, "this$0", "L" + caller.outerClassName + ";");
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "net/pnyxter/actor/dispatcher/ActorQueue$Action", "<init>", "()V");

			i = 0;
			for (String a : caller.parameterDesc) {
				i++;
				mv.visitVarInsn(ALOAD, 0);
				mv.visitLdcInsn(Type.getType("L" + a + ";"));
				mv.visitVarInsn(ALOAD, 2);
				mv.visitMethodInsn(INVOKESTATIC, "net/pnyxter/immutalizer/Immutalizer", "ensureImmutable", "(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;");
				mv.visitTypeInsn(CHECKCAST, a);
				mv.visitFieldInsn(PUTFIELD, fullInnerClassName, "a" + i, "L" + a + ";");
			}
			mv.visitInsn(RETURN);
			Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitLocalVariable("this", "Lnet/pnyxter/actor/Logger$Caller_logString;", null, l0, l3, 0);
			i = 0;
			for (String a : caller.parameterDesc) {
				i++;
				mv.visitLocalVariable("a" + i, "L" + a + ";", null, l0, l3, 1 + i);
			}
			mv.visitMaxs(0, 0); // COMPUTE_MAXS
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC, "execute", "()V", null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(caller.line, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, fullInnerClassName, "this$0", "L" + caller.outerClassName + ";");

			i = 0;
			for (String a : caller.parameterDesc) {
				i++;
				mv.visitVarInsn(ALOAD, 0);
				mv.visitFieldInsn(GETFIELD, fullInnerClassName, "a" + i, "L" + a + ";");
			}

			mv.visitMethodInsn(INVOKEVIRTUAL, caller.outerClassName, IN_ACTOR_PREFIX + caller.methodName, caller.methodDesc);
			mv.visitInsn(RETURN);

			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitLocalVariable("this", "L" + fullInnerClassName + ";", null, l0, l2, 0);
			mv.visitMaxs(0, 0); // COMPUTE_MAXS
			mv.visitEnd();
		}
		cw.visitEnd();

		return cw.toByteArray();
	}

	public static byte[] weaveActor(final String className, byte[] b, final ActorClassLoader actorLoader) {

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		ClassVisitor cv = new ClassVisitor(ASM4, cw) {
			boolean actor = false;

			String source = null;

			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if (!actor && ACTOR_DESC.equals(desc)) {
					actor = true;
					return IGNORE_ANNOTATION;
				} else {
					return super.visitAnnotation(desc, visible);
				}
			}

			@Override
			public void visitSource(String source, String debug) {
				this.source = source;
				super.visitSource(source, debug);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, final String[] exceptionsArray) {

				if (actor) {
					return new MethodNode(ASM4, access, name, desc, signature, exceptionsArray) {
						int line = 0;
						boolean inbox = false;

						@Override
						public void visitLineNumber(int line, Label start) {
							if (!inbox) {
								this.line = line;
							}
							super.visitLineNumber(line, start);
						}

						@SuppressWarnings("unchecked")
						@Override
						public void visitEnd() {
							super.visitEnd();

							for (AnnotationNode a : (List<AnnotationNode>) invisibleAnnotations) {
								if (INBOX_DESC.equals(a.desc)) {
									inbox = true;
								}
							}
							if (!inbox) {
								accept(cv);
							} else {

								CallerDescription caller = new CallerDescription(className, name, desc, signature, source, line);

								actorLoader.defineActorHelperClass(caller.getFullClassName(), createInboxCallAction(caller));

								MethodVisitor enqueueMethod = cv.visitMethod(access, name, desc, signature, exceptionsArray);
								enqueueMethod.visitEnd();

								access = 0;
								name = IN_ACTOR_PREFIX + name;

								accept(cv);
							}
						}
					};
				} else {
					return super.visitMethod(access, className, desc, signature, exceptionsArray);
				}
			}
		};

		ClassReader cr = new ClassReader(b);
		cr.accept(cv, 0);

		return cw.toByteArray();
	}
}
