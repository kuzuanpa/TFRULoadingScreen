/*
 * This file was created by <kuzuanpa>. It is distributed as part
 * of the TFRU Loading Screen Mod. Get the Source Code in github:
 * https://github.com/kuzuanpa/TFRULoadingScreen
 *
 * TFRU Loading Screen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * The source code of TFRU Loading Screen is Open Source and distributed
 * under the AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 * (Bundled assets are under CC BY-NC-SA 4.0; see LICENSE-ASSETS.)
 */

package cn.kuzuanpa.tfruloading.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites {@code Minecraft.loadScreen()} into {@link LoadingScreenBridge#renderFirstFrame()}, inserts
 * {@code startScreen()} before the first {@code FMLClientHandler.instance()} call (top of
 * {@code startGame()}, before FML discovers mods), and injects {@code pumpFrame()} into
 * {@code ProgressManager$ProgressBar.step(String)} - the only game-thread heartbeat while mods load.
 *
 * <p>{@code loadScreen} is found by name, or by shape when this runs before FML's deobfuscation: it is
 * the only no-arg method calling both {@code GL11.glOrtho} and {@code GL11.glFlush}. Class names reach
 * transformers dotted, so both forms are compared.
 */
public class LoadingScreenTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraft.client.Minecraft";
    private static final String TARGET_NOTCH = "bao";
    private static final String BRIDGE = "cn/kuzuanpa/tfruloading/loadingscreen/LoadingScreenBridge";
    private static final String BRIDGE_METHOD = "renderFirstFrame";
    private static final String BRIDGE_START = "startScreen";
    private static final String BRIDGE_PUMP = "pumpFrame";
    private static final String PROGRESS_BAR = "cpw/mods/fml/common/ProgressManager$ProgressBar";
    private static final String FML_CLIENT_HANDLER = "cpw/mods/fml/client/FMLClientHandler";
    private static final String FML_INSTANCE = "instance";
    private static final String GL11 = "org/lwjgl/opengl/GL11";

    private static final String GL_ORTHO = "glOrtho";
    private static final String GL_FLUSH = "glFlush";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        try {
            if (isMinecraft(name, transformedName)) return patchMinecraft(basicClass);
            if (isTarget(name, transformedName, PROGRESS_BAR)) return patchProgressBar(basicClass);
        } catch (Throwable t) {
            System.err.println("[tfruloading] failed to transform Minecraft.loadScreen, "
                    + "falling back to the vanilla loading screen: " + t);
        }
        return basicClass;
    }

    private static boolean isMinecraft(String name, String transformedName) {
        return isTarget(name, transformedName, TARGET) || isTarget(name, transformedName, TARGET_NOTCH);
    }

    private static boolean isTarget(String name, String transformedName, String dotted) {
        return sameClass(name, dotted) || sameClass(transformedName, dotted);
    }

    private static boolean sameClass(String candidate, String dotted) {
        if (candidate == null) return false;
        if (candidate.equals(dotted)) return true;
        return candidate.replace('.', '/').equals(dotted.replace('.', '/'));
    }

    private byte[] patchMinecraft(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        boolean touched = replaceLoadScreen(node);
        touched |= installStartScreenHook(node);
        if (!touched) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        return cw.toByteArray();
    }

    private byte[] patchProgressBar(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        for (MethodNode m : node.methods) {
            if (!"step".equals(m.name) || !"(Ljava/lang/String;)V".equals(m.desc)) continue;
            AbstractInsnNode first = m.instructions.getFirst();
            if (first instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode) first).owner)) {
                return basicClass;
            }
            m.instructions.insertBefore(first,
                    new MethodInsnNode(INVOKESTATIC, BRIDGE, BRIDGE_PUMP, "()V", false));

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(cw);
            System.out.println("[tfruloading] hooked " + node.name + ".step(String) for loading"
                    + " screen frames");
            return cw.toByteArray();
        }

        System.err.println("[tfruloading] no step(String) in " + node.name
                + "; the loading screen will only advance on mod lifecycle events");
        return basicClass;
    }

    private boolean replaceLoadScreen(ClassNode node) {
        if (alreadyReplaced(node)) return true;

        MethodNode target = findByName(node);
        if (target == null) target = findByShape(node);
        if (target == null) {
            StringBuilder seen = new StringBuilder();
            for (MethodNode m : node.methods) {
                if (seen.length() > 0) seen.append(", ");
                if (seen.length() > 220) { seen.append("..."); break; }
                seen.append(m.name).append(m.desc);
            }
            System.err.println("[tfruloading] Minecraft.loadScreen was not found in " + node.name
                    + " (methods: " + seen + "); the vanilla loading screen will show.");
            return false;
        }

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) target.localVariables.clear();
        if (target.visibleLocalVariableAnnotations != null) target.visibleLocalVariableAnnotations.clear();
        target.instructions.add(new MethodInsnNode(INVOKESTATIC, BRIDGE, BRIDGE_METHOD, "()V", false));
        target.instructions.add(new InsnNode(RETURN));

        System.out.println("[tfruloading] replaced " + node.name + "." + target.name
                + target.desc + " with the TFRU loading screen");
        return true;
    }

    private static boolean alreadyReplaced(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"()V".equals(m.desc)) continue;
            AbstractInsnNode first = m.instructions.getFirst();
            if (!(first instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) first;
            if (BRIDGE.equals(call.owner) && BRIDGE_METHOD.equals(call.name)) return true;
        }
        return false;
    }

    /** Inserts the start hook before the first FMLClientHandler.instance() call in the class. */
    private boolean installStartScreenHook(ClassNode node) {
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!FML_CLIENT_HANDLER.equals(call.owner) || !FML_INSTANCE.equals(call.name)) continue;
                AbstractInsnNode previous = call.getPrevious();
                if (previous instanceof MethodInsnNode
                        && BRIDGE.equals(((MethodInsnNode) previous).owner)
                        && BRIDGE_START.equals(((MethodInsnNode) previous).name)) {
                    return true;
                }

                m.instructions.insertBefore(call,
                        new MethodInsnNode(INVOKESTATIC, BRIDGE, BRIDGE_START, "()V", false));
                System.out.println("[tfruloading] hooked " + node.name + "." + m.name
                        + " to start the loading screen before mod loading");
                return true;
            }
        }
        System.err.println("[tfruloading] no FMLClientHandler.instance() call in " + node.name
                + "; the loading screen will start with the first mod lifecycle event instead");
        return false;
    }

    /** MCP name in a dev workspace, SRG name once FML has deobfuscated. */
    private static MethodNode findByName(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"()V".equals(m.desc)) continue;
            if ("loadScreen".equals(m.name) || "func_71357_I".equals(m.name)) return m;
        }
        return null;
    }

    /** Name-agnostic fallback: loadScreen is the only no-arg method using both glOrtho and glFlush. */
    private static MethodNode findByShape(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"()V".equals(m.desc)) continue;
            boolean ortho = false, flush = false;
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!GL11.equals(call.owner)) continue;
                if (GL_ORTHO.equals(call.name)) ortho = true;
                if (GL_FLUSH.equals(call.name)) flush = true;
            }
            if (ortho && flush) return m;
        }
        return null;
    }
}
