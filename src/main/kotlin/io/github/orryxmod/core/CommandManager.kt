package io.github.orryxmod.core

import io.github.orryxmod.core.registry.FeatureRegistry
import io.github.orryxmod.feature.aim.AimConfig
import io.github.orryxmod.feature.aim.AimFeature
import io.github.orryxmod.feature.aim.AimModule
import io.github.orryxmod.feature.bloom.BloomConfig
import io.github.orryxmod.feature.bloom.BloomConfigManager
import io.github.orryxmod.feature.bloom.BloomFeature
import io.github.orryxmod.feature.effect.EffectFeature
import io.github.orryxmod.feature.effect.FlickerConfig
import io.github.orryxmod.feature.effect.GhostConfig
import io.github.orryxmod.feature.mouse.MouseFeature
import io.github.orryxmod.feature.navigation.NavigationFeature
import io.github.orryxmod.feature.shockwave.ShockwaveFeature
import io.github.orryxmod.util.MC
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.joml.Vector3d

object CommandManager {

    const val PREFIX = "."
    private val TAG = "${TextFormatting.GOLD}[OrryxMod]${TextFormatting.RESET} "

    private fun sendMessage(message: String) {
        MC.player?.sendMessage(TextComponentString("$TAG$message"))
    }

    private fun sendError(message: String) {
        MC.player?.sendMessage(TextComponentString("$TAG${TextFormatting.RED}$message"))
    }

    private fun sendSuccess(message: String) {
        MC.player?.sendMessage(TextComponentString("$TAG${TextFormatting.GREEN}$message"))
    }

    private fun sendInfo(message: String) {
        MC.player?.sendMessage(TextComponentString("$TAG${TextFormatting.GRAY}$message"))
    }

    @SubscribeEvent
    fun onChat(event: ClientChatEvent) {
        val message = event.message
        if (!message.startsWith(PREFIX)) return

        event.isCanceled = true

        // 手动添加到聊天历史记录，以便可以使用上下键浏览
        MC.ingameGUI?.chatGUI?.addToSentMessages(message)

        val args = message.split(" ")
        when (val command = args[0].removePrefix(PREFIX)) {
            "features" -> {
                val features = FeatureRegistry.getAll()
                sendMessage("Registered features (${features.size}):")
                features.forEach { feature ->
                    val status = if (feature.enabled) "${TextFormatting.GREEN}enabled" else "${TextFormatting.RED}disabled"
                    sendInfo("  - ${TextFormatting.AQUA}${feature.metadata.id}${TextFormatting.GRAY}: ${feature.metadata.description} [$status${TextFormatting.GRAY}]")
                }
            }
            "enable" -> {
                if (args.size < 2) {
                    sendError("Usage: .enable <feature_id>")
                    return
                }
                val featureId = args[1]
                val feature = FeatureRegistry.get(featureId)
                if (feature != null) {
                    feature.enable()
                    sendSuccess("Enabled feature: $featureId")
                } else {
                    sendError("Feature not found: $featureId")
                }
            }
            "disable" -> {
                if (args.size < 2) {
                    sendError("Usage: .disable <feature_id>")
                    return
                }
                val featureId = args[1]
                val feature = FeatureRegistry.get(featureId)
                if (feature != null) {
                    feature.disable()
                    sendSuccess("Disabled feature: $featureId")
                } else {
                    sendError("Feature not found: $featureId")
                }
            }

            // ========== 冲击波测试命令 ==========
            "shock", "shockwave" -> {
                val player = MC.player ?: return
                val radius = args.getOrNull(1)?.toDoubleOrNull() ?: 5.0
                val x = player.posX
                val y = player.posY - 0.2
                val z = player.posZ
                ShockwaveFeature.circleSlamFracture(x, y, z, radius)
                sendSuccess("Circle shockwave: radius=$radius")
            }
            "shock2", "squareshock" -> {
                val player = MC.player ?: return
                val length = args.getOrNull(1)?.toDoubleOrNull() ?: 5.0
                val width = args.getOrNull(2)?.toDoubleOrNull() ?: 3.0
                val x = player.posX
                val y = player.posY - 0.2
                val z = player.posZ
                val yaw = player.rotationYaw.toDouble()
                ShockwaveFeature.squareSlamFracture(x, y, z, length, width, yaw)
                sendSuccess("Square shockwave: ${length}x$width")
            }
            "shock3", "sectorshock" -> {
                val player = MC.player ?: return
                val radius = args.getOrNull(1)?.toDoubleOrNull() ?: 5.0
                val angle = args.getOrNull(2)?.toDoubleOrNull() ?: 90.0
                val x = player.posX
                val y = player.posY - 0.2
                val z = player.posZ
                val yaw = player.rotationYaw.toDouble()
                ShockwaveFeature.sectorSlamFracture(x, y, z, radius, angle, yaw)
                sendSuccess("Sector shockwave: radius=$radius, angle=$angle")
            }

            // ========== 实体效果测试命令 ==========
            "ghost" -> {
                val player = MC.player ?: return
                val duration = args.getOrNull(1)?.toLongOrNull() ?: 3000L
                val density = args.getOrNull(2)?.toIntOrNull() ?: 5
                EffectFeature.applyGhost(player.uniqueID, duration, GhostConfig(density = density, gap = 0))
                sendSuccess("Ghost effect: ${duration}ms, density=$density")
            }
            "flicker" -> {
                val player = MC.player ?: return
                val duration = args.getOrNull(1)?.toLongOrNull() ?: 3000L
                val alpha = args.getOrNull(2)?.toFloatOrNull() ?: 0.5f
                EffectFeature.applyFlicker(player.uniqueID, duration, FlickerConfig(alpha = alpha))
                sendSuccess("Flicker effect: ${duration}ms, alpha=$alpha")
            }
            "shadow" -> {
                val player = MC.player ?: return
                val duration = args.getOrNull(1)?.toLongOrNull() ?: 5000L
                val offsetX = args.getOrNull(2)?.toDoubleOrNull() ?: 2.0
                EffectFeature.addShadow(
                    player.uniqueID,
                    "test",
                    Vector3d(player.posX + offsetX, player.posY, player.posZ),
                    timeout = duration
                )
                sendSuccess("Shadow added: offset=$offsetX, ${duration}ms")
            }
            "clearshadow" -> {
                val player = MC.player ?: return
                EffectFeature.clearShadows(player.uniqueID)
                sendSuccess("Shadows cleared")
            }
            "entityshow" -> {
                val player = MC.player ?: return
                val duration = args.getOrNull(1)?.toLongOrNull() ?: 3000L
                val alpha = args.getOrNull(2)?.toFloatOrNull() ?: 0.8f
                val fadeOut = args.getOrNull(3)?.lowercase() != "false"
                val offsetX = args.getOrNull(4)?.toDoubleOrNull() ?: 1.5
                EffectFeature.addShadow(
                    uuid = player.uniqueID,
                    group = "test-entityshow",
                    position = Vector3d(player.posX + offsetX, player.posY, player.posZ),
                    timeout = duration,
                    alpha = alpha,
                    fadeOut = fadeOut
                )
                sendSuccess("EntityShow: alpha=$alpha, fadeOut=$fadeOut, ${duration}ms")
            }

            // ========== 瞄准系统测试命令 ==========
            "aim" -> {
                val mode = args.getOrNull(1) ?: "point"
                val module = when (mode.lowercase()) {
                    "point", "p" -> AimModule.POINT
                    "direction", "dir", "d" -> AimModule.DIRECTION
                    "area", "a" -> AimModule.AREA
                    else -> AimModule.POINT
                }
                val scale = args.getOrNull(2)?.toDoubleOrNull() ?: 1.0
                val maxDist = args.getOrNull(3)?.toDoubleOrNull() ?: 50.0
                AimFeature.startAiming("test", module, AimConfig(scale = scale, maxDistance = maxDist))
                sendSuccess("Aim started: $mode")
                sendInfo("Left click = confirm, Right click/ESC = cancel")
            }
            "cancelaim" -> {
                AimFeature.cancel()
                sendSuccess("Aim cancelled")
            }

            // ========== 导航系统测试命令 ==========
            "nav", "navigate" -> {
                val player = MC.player ?: return
                val x = args.getOrNull(1)?.toIntOrNull() ?: (player.posX.toInt() + 10)
                val y = args.getOrNull(2)?.toIntOrNull() ?: player.posY.toInt()
                val z = args.getOrNull(3)?.toIntOrNull() ?: player.posZ.toInt()
                NavigationFeature.startNavigation(BlockPos(x, y, z))
                sendSuccess("Navigation to ($x, $y, $z)")
            }
            "stopnav" -> {
                NavigationFeature.stopNavigation()
                sendSuccess("Navigation stopped")
            }

            // ========== 鼠标控制测试命令 ==========
            "mouse" -> {
                val action = args.getOrNull(1)?.lowercase() ?: "toggle"
                when (action) {
                    "show", "on", "1" -> {
                        MouseFeature.setCursorVisible(true)
                        sendSuccess("Mouse cursor shown")
                        sendInfo("Press M or use .mouse hide to hide")
                    }
                    "hide", "off", "0" -> {
                        MouseFeature.setCursorVisible(false)
                        sendSuccess("Mouse cursor hidden")
                    }
                    "toggle", "t" -> {
                        MouseFeature.toggleCursor()
                        val state = if (MouseFeature.isVisible()) "shown" else "hidden"
                        sendSuccess("Mouse cursor $state")
                    }
                    else -> {
                        sendError("Usage: .mouse [show|hide|toggle]")
                    }
                }
            }

            // ========== 泛光测试命令 ==========
            "bloom" -> {
                val action = args.getOrNull(1)?.lowercase() ?: "toggle"
                when (action) {
                    "on", "1", "enable" -> {
                        BloomFeature.Config.enabled = true
                        sendSuccess("Bloom enabled")
                    }
                    "off", "0", "disable" -> {
                        BloomFeature.Config.enabled = false
                        sendSuccess("Bloom disabled")
                    }
                    "toggle", "t" -> {
                        BloomFeature.Config.enabled = !BloomFeature.Config.enabled
                        val state = if (BloomFeature.Config.enabled) "enabled" else "disabled"
                        sendSuccess("Bloom $state")
                    }
                    "status", "s" -> {
                        val state = if (BloomFeature.Config.enabled) "${TextFormatting.GREEN}enabled" else "${TextFormatting.RED}disabled"
                        sendMessage("Bloom: $state")
                        sendInfo("Max entities: ${BloomFeature.Config.maxBloomEntities} (0=unlimited)")
                    }
                    else -> {
                        sendError("Usage: .bloom [on|off|toggle|status]")
                    }
                }
            }
            "bloomadd" -> {
                // .bloomadd <name> [r] [g] [b] [strength] [radius] [priority]
                val name = args.getOrNull(1)
                if (name == null) {
                    sendError("Usage: .bloomadd <name> [r] [g] [b] [strength] [radius] [priority]")
                    return
                }
                val r = args.getOrNull(2)?.toIntOrNull() ?: 200
                val g = args.getOrNull(3)?.toIntOrNull() ?: 200
                val b = args.getOrNull(4)?.toIntOrNull() ?: 200
                val strength = args.getOrNull(5)?.toFloatOrNull() ?: 1.0f
                val radius = args.getOrNull(6)?.toFloatOrNull() ?: 32.0f
                val priority = args.getOrNull(7)?.toIntOrNull() ?: 0
                val config = BloomConfig(
                    name = name,
                    color = intArrayOf(r, g, b, 255),
                    strength = strength,
                    radius = radius,
                    priority = priority
                )
                BloomConfigManager.update(name, config)
                sendSuccess("Bloom config added: $name")
                sendInfo("Color: RGB($r,$g,$b), strength=$strength, radius=$radius, priority=$priority")
            }
            "bloomremove" -> {
                val name = args.getOrNull(1)
                if (name == null) {
                    sendError("Usage: .bloomremove <name>")
                    return
                }
                BloomConfigManager.remove(name)
                sendSuccess("Bloom config removed: $name")
            }
            "bloomclear" -> {
                BloomConfigManager.clear()
                sendSuccess("All bloom configs cleared")
            }
            "bloomlist" -> {
                if (!BloomConfigManager.hasConfigs()) {
                    sendMessage("No bloom configs")
                    return
                }
                sendMessage("Bloom configs:")
                // 由于 BloomConfigManager 没有暴露遍历方法，这里只能显示有配置
                sendInfo("Use .bloomadd to add configs")
            }
            "bloomtest" -> {
                // 为玩家自己添加泛光测试
                val player = MC.player ?: return
                val playerName = player.name
                val r = args.getOrNull(1)?.toIntOrNull() ?: 0
                val g = args.getOrNull(2)?.toIntOrNull() ?: 200
                val b = args.getOrNull(3)?.toIntOrNull() ?: 200
                val strength = args.getOrNull(4)?.toFloatOrNull() ?: 1.5f
                val config = BloomConfig(
                    name = playerName,
                    color = intArrayOf(r, g, b, 255),
                    strength = strength,
                    radius = 64.0f,
                    priority = 100
                )
                BloomConfigManager.update(playerName, config)
                sendSuccess("Bloom test applied to self")
                sendInfo("Color: RGB($r,$g,$b), strength=$strength")
                sendInfo("Use .bloomremove $playerName to remove")
            }
            "bloommax" -> {
                val max = args.getOrNull(1)?.toIntOrNull()
                if (max == null) {
                    sendMessage("Max bloom entities: ${BloomFeature.Config.maxBloomEntities} (0=unlimited)")
                    return
                }
                BloomFeature.Config.maxBloomEntities = max
                sendSuccess("Max bloom entities set to: $max")
            }

            // ========== 帮助 ==========
            "help" -> {
                sendMessage("${TextFormatting.YELLOW}===== Commands =====")
                sendInfo("${TextFormatting.WHITE}--- Feature ---")
                sendInfo("  .features / .enable <id> / .disable <id>")
                sendInfo("${TextFormatting.WHITE}--- Shockwave ---")
                sendInfo("  .shock [r] / .shock2 [l] [w] / .shock3 [r] [angle]")
                sendInfo("${TextFormatting.WHITE}--- Effects ---")
                sendInfo("  .ghost [ms] [density] / .flicker [ms] [alpha]")
                sendInfo("  .shadow [ms] [offsetX] / .clearshadow")
                sendInfo("  .entityshow [ms] [alpha] [fadeOut] [offsetX]")
                sendInfo("${TextFormatting.WHITE}--- Aim ---")
                sendInfo("  .aim [point|dir|area] / .cancelaim")
                sendInfo("${TextFormatting.WHITE}--- Navigation ---")
                sendInfo("  .nav [x] [y] [z] / .stopnav")
                sendInfo("${TextFormatting.WHITE}--- Mouse ---")
                sendInfo("  .mouse [show|hide|toggle]")
                sendInfo("${TextFormatting.WHITE}--- Bloom ---")
                sendInfo("  .bloom [on|off|toggle|status]")
                sendInfo("  .bloomadd <name> [r] [g] [b] [strength] [radius] [priority]")
                sendInfo("  .bloomremove <name> / .bloomclear / .bloomlist")
                sendInfo("  .bloomtest [r] [g] [b] [strength] / .bloommax [n]")
            }

            else -> {
                sendError("Unknown command: $command")
                sendInfo("Type .help for commands")
            }
        }
    }
}