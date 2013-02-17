package net.pnyxter.actor.instrument;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;

public class ActorWeaver implements Opcodes {

	private static final String IN_ACTOR_PREFIX = "__in_actor__";

	private static final String ACTOR_DESC = Type.getDescriptor(Actor.class);
	private static final String INBOX_DESC = Type.getDescriptor(Inbox.class);
	private static final String FUTURE_DESC = Type.getDescriptor(Future.class);

	private static final AnnotationVisitor IGNORE_ANNOTATION = new AnnotationVisitor(ASM4) {
	};

	private static class CallerDescription extends SignatureVisitor {

		final int classVersion;

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

		public CallerDescription(int classVersion, String outerClassName, String name, String description, String signature, String source, int line) throws IllegalInboxMethodException {
			super(ASM4);

			this.classVersion = classVersion;

			this.outerClassName = outerClassName;
			this.source = source;
			this.line = line;

			this.methodName = name;
			this.methodDesc = description;

			if (signature == null) {
				this.methodSignature = description;
			} else {
				this.methodSignature = signature;
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
						visitEnd();
					}
				}

				@Override
				public void visitClassType(String name) {
					if (desc == null) {
						desc = array + "L" + name + ";";
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

		System.out.println("Building inbox caller class: " + caller.className);

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		FieldVisitor fv;
		MethodVisitor mv;

		String innerClassName = caller.className;
		String fullInnerClassName = caller.getFullClassName();

		cw.visit(caller.classVersion, ACC_FINAL + ACC_SUPER, fullInnerClassName, null, "java/lang/Object", new String[] { "net/pnyxter/actor/dispatcher/ActorQueue$Action" });

		cw.visitSource(caller.source, null);

		cw.visitInnerClass(fullInnerClassName, caller.outerClassName, innerClassName, ACC_PRIVATE + ACC_FINAL);

		cw.visitInnerClass("net/pnyxter/actor/dispatcher/ActorQueue$Action", "net/pnyxter/actor/dispatcher/ActorQueue", "Action", ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT + ACC_INTERFACE);

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

				if (a.startsWith("L")) {
					mv.visitLdcInsn(Type.getType(a));
					mv.visitVarInsn(ALOAD, i + 1);
					mv.visitMethodInsn(INVOKESTATIC, "net/pnyxter/immutalizer/Immutalizer", "ensureImmutable", "(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;");
					mv.visitTypeInsn(CHECKCAST, a);
				} else {
					switch (a) {
					case "I":
					case "B":
					case "Z":
						mv.visitVarInsn(ILOAD, i + 1);
						break;
					case "J":
						mv.visitVarInsn(LLOAD, i + 1);
						break;
					case "F":
						mv.visitVarInsn(FLOAD, i + 1);
						break;
					case "D":
						mv.visitVarInsn(DLOAD, i + 1);
						break;
					default:
						throw new ClassFormatError("Unsuported type on actor method: " + a);
					}
				}

				mv.visitFieldInsn(PUTFIELD, fullInnerClassName, "a" + i, a);
			}
			mv.visitInsn(RETURN);
			Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitLocalVariable("this", "L" + fullInnerClassName + ";", null, l0, l3, 0);
			i = 0;
			for (String a : caller.parameterDesc) {
				i++;
				mv.visitLocalVariable("a" + i, a, null, l0, l3, 1 + i);
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
				mv.visitFieldInsn(GETFIELD, fullInnerClassName, "a" + i, a);
			}

			mv.visitMethodInsn(INVOKEVIRTUAL, caller.outerClassName, IN_ACTOR_PREFIX + caller.methodName, caller.methodDesc);
			mv.visitInsn(RETURN);

			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitLocalVariable("this$0", "L" + fullInnerClassName + ";", null, l0, l2, 0);
			mv.visitMaxs(0, 0); // COMPUTE_MAXS
			mv.visitEnd();
		}
		cw.visitEnd();

		new ClassReader(cw.toByteArray()).accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);

		return cw.toByteArray();
	}

	public static byte[] weaveActor(final String className, byte[] b, final ClassDefiner actorLoader) {

		ClassReader cr = new ClassReader(b);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		System.out.println("Reading: " + className);

		final AtomicBoolean actor = new AtomicBoolean(false);

		ClassVisitor cv = new ClassVisitor(ASM4, cw) {
			boolean fieldsAdded = false;
			String source = null;
			int classVersion;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				classVersion = version;

				if (interfaces == null) {
					interfaces = new String[1];
				} else {
					interfaces = Arrays.copyOf(interfaces, interfaces.length + 1);
				}
				interfaces[interfaces.length - 1] = "net/pnyxter/actor/dispatcher/ActorRef";

				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if (!actor.get() && ACTOR_DESC.equals(desc)) {

					System.out.println("Actor detected: " + className);

					actor.set(true);
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

			private void addActorExtensions() {
				if (!fieldsAdded) {
					fieldsAdded = true;

					System.out.println("Fields and methods added to actor: " + className);

					super.visitField(ACC_PRIVATE + ACC_FINAL + ACC_TRANSIENT, IN_ACTOR_PREFIX + "queue", "Lnet/pnyxter/actor/dispatcher/ActorQueue;", null, null).visitEnd();
					super.visitField(ACC_PRIVATE + ACC_FINAL + ACC_TRANSIENT, IN_ACTOR_PREFIX + "spawner", "Lnet/pnyxter/actor/dispatcher/ActorRef;", null, null).visitEnd();
					super.visitField(ACC_PRIVATE + ACC_TRANSIENT, IN_ACTOR_PREFIX + "assigned_thread", "Ljava/lang/Thread;", null, null).visitEnd();

					{
						MethodVisitor mv = super.visitMethod(ACC_PUBLIC + ACC_FINAL, "getAssignedThread", "()Ljava/lang/Thread;", null, null);
						mv.visitCode();
						Label l0 = new Label();
						mv.visitLabel(l0);
						mv.visitVarInsn(ALOAD, 0);
						mv.visitFieldInsn(GETFIELD, className, IN_ACTOR_PREFIX + "assigned_thread", "Ljava/lang/Thread;");
						mv.visitInsn(ARETURN);
						Label l1 = new Label();
						mv.visitLabel(l1);
						mv.visitLocalVariable("this", "L" + className + ";", null, l0, l1, 0);
						mv.visitMaxs(0, 0); // COMPUTE_MAXS
						mv.visitEnd();
					}
					{
						MethodVisitor mv = super.visitMethod(ACC_PUBLIC, "setAssignedThread", "(Ljava/lang/Thread;)V", null, null);
						mv.visitCode();
						Label l0 = new Label();
						mv.visitLabel(l0);
						mv.visitVarInsn(ALOAD, 0);
						mv.visitFieldInsn(GETFIELD, className, IN_ACTOR_PREFIX + "assigned_thread", "Ljava/lang/Thread;");
						Label l1 = new Label();
						mv.visitJumpInsn(IFNONNULL, l1);
						Label l2 = new Label();
						mv.visitLabel(l2);
						mv.visitVarInsn(ALOAD, 0);
						mv.visitVarInsn(ALOAD, 1);
						mv.visitFieldInsn(PUTFIELD, className, IN_ACTOR_PREFIX + "assigned_thread", "Ljava/lang/Thread;");
						mv.visitLabel(l1);
						mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
						mv.visitInsn(RETURN);
						Label l3 = new Label();
						mv.visitLabel(l3);
						mv.visitLocalVariable("this", "L" + className + ";", null, l0, l3, 0);
						mv.visitLocalVariable("thread", "Ljava/lang/Thread;", null, l0, l3, 1);
						mv.visitMaxs(0, 0); // COMPUTE_MAXS
						mv.visitEnd();
					}
				}
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, final String[] exceptionsArray) {
				// System.out.println("Scanning: " + className + "#" + name);

				if (actor.get()) {

					addActorExtensions();

					if ("<init>".equals(name)) {
						return new MethodVisitor(ASM4, super.visitMethod(access, name, desc, signature, exceptionsArray)) {
							boolean superCalled = false;

							int currentLine = 0;

							@Override
							public void visitLineNumber(int line, Label start) {
								currentLine = line;
								super.visitLineNumber(line, start);
							}

							@Override
							public void visitMethodInsn(int opcode, String owner, String name, String desc) {
								super.visitMethodInsn(opcode, owner, name, desc);

								if (!superCalled && "<init>".equals(name)) {
									superCalled = true;

									Label l1 = new Label();
									mv.visitLabel(l1);
									mv.visitLineNumber(currentLine, l1);

									super.visitVarInsn(ALOAD, 0);
									super.visitTypeInsn(NEW, "net/pnyxter/actor/dispatcher/ActorQueue");
									super.visitInsn(DUP);
									super.visitMethodInsn(INVOKESPECIAL, "net/pnyxter/actor/dispatcher/ActorQueue", "<init>", "()V");
									super.visitFieldInsn(PUTFIELD, className, IN_ACTOR_PREFIX + "queue", "Lnet/pnyxter/actor/dispatcher/ActorQueue;");

									Label l2 = new Label();
									mv.visitLabel(l2);
									mv.visitLineNumber(currentLine, l2);

									super.visitVarInsn(ALOAD, 0);
									super.visitMethodInsn(INVOKESTATIC, "net/pnyxter/actor/dispatcher/ActorThreads", "getCurrentActor", "()Lnet/pnyxter/actor/dispatcher/ActorRef;");
									super.visitFieldInsn(PUTFIELD, className, IN_ACTOR_PREFIX + "spawner", "Lnet/pnyxter/actor/dispatcher/ActorRef;");
								}
							}

						};

					}

					return new MethodNode(ASM4, access, name, desc, signature, exceptionsArray) {
						int line = 0;
						boolean inbox = false;

						@Override
						public void visitLineNumber(int line, Label start) {
							if (this.line == 0) {

								System.out.println("First line detected: " + source + ":" + line);

								this.line = line;
							}
							super.visitLineNumber(line, start);
						}

						@Override
						public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
							if (INBOX_DESC.equals(desc)) {
								System.out.println("Inbox detected: " + className + "#" + name);
								inbox = true;

								return IGNORE_ANNOTATION;
							}
							return super.visitAnnotation(desc, visible);
						}

						@Override
						public void visitEnd() {
							super.visitEnd();

							if (!inbox) {
								System.out.println("Forward plain method: " + className + "#" + name);

								accept(cv);
							} else {
								System.out.println("Create inbox method: " + className + "#" + name);

								CallerDescription caller = new CallerDescription(classVersion, className, name, desc, signature, source, line);

								actorLoader.defineClass(Type.getObjectType(caller.getFullClassName()).getClassName(), createInboxCallAction(caller));

								MethodVisitor enqueueMethod = cv.visitMethod(access, name, desc, signature, exceptionsArray);
								enqueueMethod.visitCode();
								Label l0 = new Label();
								enqueueMethod.visitLabel(l0);
								enqueueMethod.visitLineNumber(line, l0);
								enqueueMethod.visitVarInsn(ALOAD, 0);
								enqueueMethod.visitFieldInsn(GETFIELD, caller.outerClassName, IN_ACTOR_PREFIX + "queue", "Lnet/pnyxter/actor/dispatcher/ActorQueue;");
								enqueueMethod.visitTypeInsn(NEW, caller.getFullClassName());
								enqueueMethod.visitInsn(DUP);

								enqueueMethod.visitVarInsn(ALOAD, 0);
								int i = 0;
								for (String a : caller.parameterDesc) {
									i++;

									if (a.startsWith("L")) {
										enqueueMethod.visitVarInsn(ALOAD, i);
									} else {
										switch (a) {
										case "I":
										case "B":
										case "Z":
											enqueueMethod.visitVarInsn(ILOAD, i);
											break;
										case "J":
											enqueueMethod.visitVarInsn(LLOAD, i);
											break;
										case "F":
											enqueueMethod.visitVarInsn(FLOAD, i);
											break;
										case "D":
											enqueueMethod.visitVarInsn(DLOAD, i);
											break;
										default:
											throw new ClassFormatError("Unsuported type on actor method (building action): " + a);
										}
									}
								}

								enqueueMethod.visitMethodInsn(INVOKESPECIAL, caller.getFullClassName(), "<init>", caller.constructorSignature);
								enqueueMethod.visitMethodInsn(INVOKEVIRTUAL, "net/pnyxter/actor/dispatcher/ActorQueue", "add", "(Lnet/pnyxter/actor/dispatcher/ActorQueue$Action;)V");
								enqueueMethod.visitInsn(RETURN);
								Label l2 = new Label();
								enqueueMethod.visitLabel(l2);
								enqueueMethod.visitLocalVariable("this", "L" + caller.outerClassName + ";", null, l0, l2, 0);
								i = 0;
								for (String pDesc : caller.parameterDesc) {
									i++;
									enqueueMethod.visitLocalVariable("a" + i, pDesc, null, l0, l2, 1);
								}

								enqueueMethod.visitMaxs(0, 0); // COMPUTE_MAXS
								enqueueMethod.visitEnd();

								access = 0;
								name = IN_ACTOR_PREFIX + name;

								System.out.println("Create execution method: " + className + "#" + name);

								accept(cv);
							}
						}
					};
				} else {
					return super.visitMethod(access, name, desc, signature, exceptionsArray);
				}
			}
		};

		cr.accept(cv, 0);

		if (actor.get()) {
			new ClassReader(cw.toByteArray()).accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);

			return cw.toByteArray();
		}
		return null;
	}
}
