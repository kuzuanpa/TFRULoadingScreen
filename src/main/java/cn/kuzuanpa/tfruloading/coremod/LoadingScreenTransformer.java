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
 * Rewrites three things:
 *
 * <ol>
 *   <li>the body of {@code Minecraft.loadScreen()} (the vanilla Mojang-logo screen) becomes a call
 *       into {@link cn.kuzuanpa.tfruloading.loadingscreen.LoadingScreenBridge} followed by a return.
 *       With Forge's splash disabled {@code SplashProgress.drawVanillaScreen()} calls that method,
 *       so this is exactly the point where the vanilla first frame used to be drawn.</li>
 *   <li>a call to {@code LoadingScreenBridge.startScreen()} is inserted before the first
 *       {@code FMLClientHandler.instance()} call, i.e. at the top of {@code startGame()} right after
 *       the window exists and right before FML starts discovering and constructing mods. This is the
 *       anchor BetterLoadingScreen uses for its first progress draw, and it is what keeps the black
 *       window short: {@code drawVanillaScreen()} only runs after every mod has been constructed and
 *       pre-initialized (13 seconds on the real TFRU pack).</li>
 *   <li>a call to {@code LoadingScreenBridge.pumpFrame()} is inserted at the top of FML's
 *       {@code ProgressManager$ProgressBar.step(String)}. Mod lifecycle events alone are not enough
 *       - the real pack fires none at all for 55 seconds - but FML's progress bars are stepped
 *       thousands of times during a load, on the game thread, which is the only thread allowed to
 *       draw (see the failed shared-context attempt in ROADMAP §2.13).</li>
 * </ol>
 *
 * <p>Finding that method is the whole trick, and the first cut got it wrong: in a real client the
 * coremod transformer runs *before* FML's deobfuscation transformer, so the class is still
 * notch-obfuscated ({@code bao.aj()V}) and neither the MCP name ({@code loadScreen}) nor the SRG
 * name ({@code func_71357_I}) matches - the real client log said
 * "Minecraft.loadScreen was not found" and silently kept the vanilla screen. The method is therefore
 * matched by <em>shape</em> as a fallback: it is the only no-arg method that sets up an ortho
 * projection and then flushes GL ({@code GL11.glOrtho} + {@code GL11.glFlush}). LWJGL is never
 * obfuscated, so the shape is stable at every stage of the transform chain. (Matching on the
 * declared {@code LWJGLException} does <em>not</em> work: the vanilla 1.7.10 method declares none.)
 */
public class LoadingScreenTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraft.client.Minecraft";
    private static final String TARGET_NOTCH = "bao";   // the notch name of Minecraft in 1.7.10
    private static final String BRIDGE = "cn/kuzuanpa/tfruloading/loadingscreen/LoadingScreenBridge";
    private static final String BRIDGE_METHOD = "renderFirstFrame";
    private static final String BRIDGE_START = "startScreen";
    private static final String BRIDGE_PUMP = "pumpFrame";
    private static final String PROGRESS_BAR = "cpw/mods/fml/common/ProgressManager$ProgressBar";
    private static final String FML_CLIENT_HANDLER = "cpw/mods/fml/client/FMLClientHandler";
    private static final String FML_INSTANCE = "instance";
    private static final String LWJGL_EXCEPTION = "org/lwjgl/LWJGLException";
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

    /**
     * Compares a launch-class-loader class name with a dotted name.
     *
     * <p>This matters and bit us once: {@code LaunchClassLoader} hands transformers the <em>dotted</em>
     * binary name ({@code cpw.mods.fml.common.ProgressManager$ProgressBar}), while the internal name
     * uses slashes. The first cut compared against the slash form, silently never matched the progress
     * bar, and the loading screen fell back to "one frame per mod lifecycle event" (0-7 fps in the
     * real client, see ROADMAP §2.13.5). Accept both forms.
     */
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
        touched |= installEarlyPumpHook(node);
        if (!touched) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Adds a frame opportunity to every FML progress-bar step. {@code step} is called from the game
     * thread by FML and by any mod that reports progress, so this is the heartbeat that keeps the
     * picture moving while a mod is inside its own long handler.
     */
    private byte[] patchProgressBar(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        for (MethodNode m : node.methods) {
            if (!"step".equals(m.name) || !"(Ljava/lang/String;)V".equals(m.desc)) continue;
            AbstractInsnNode first = m.instructions.getFirst();
            if (first instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode) first).owner)) {
                return basicClass; // already hooked by a previous pass
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

    /** Returns true when {@code loadScreen} was found and rewritten. */
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

    /** True when a previous pass already rewrote {@code loadScreen} into our bridge call. */
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

    /**
     * Inserts {@code LoadingScreenBridge.startScreen()} before the first
     * {@code FMLClientHandler.instance()} call in the class. {@code FMLClientHandler} is FML code and
     * therefore never obfuscated, so the anchor holds in notch, SRG and MCP states alike.
     */
    private boolean installEarlyPumpHook(ClassNode node) {
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!FML_CLIENT_HANDLER.equals(call.owner) || !FML_INSTANCE.equals(call.name)) continue;
                AbstractInsnNode previous = call.getPrevious();
                if (previous instanceof MethodInsnNode
                        && BRIDGE.equals(((MethodInsnNode) previous).owner)
                        && BRIDGE_START.equals(((MethodInsnNode) previous).name)) {
                    return true; // a previous pass already hooked this call site
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

    /** MCP name in a dev workspace, SRG name in a production run that has already been remapped. */
    private static MethodNode findByName(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"()V".equals(m.desc)) continue;
            if ("loadScreen".equals(m.name) || "func_71357_I".equals(m.name)) return m;
        }
        return null;
    }

    /**
     * Name-agnostic fallback; see the class javadoc. Requires both GL11.glOrtho and GL11.glFlush in
     * the same no-arg method: that pair is loadScreen's signature. The other GL-flushing no-arg
     * method in Minecraft is the main tick loop, which does not touch the projection matrix.
     */
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
