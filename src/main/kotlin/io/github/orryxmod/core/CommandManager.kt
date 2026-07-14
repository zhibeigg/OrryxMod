package io.github.orryxmod.core

import io.github.orryxmod.core.registry.FeatureRegistry
import io.github.orryxmod.feature.aim.AimConfig
import io.github.orryxmod.feature.aim.AimFeature
import io.github.orryxmod.feature.aim.AimModule
import io.github.orryxmod.feature.aim.AimState
import io.github.orryxmod.feature.aim.IndicatorType
import io.github.orryxmod.feature.bloom.BloomConfig
import io.github.orryxmod.feature.bloom.BloomConfigManager
import io.github.orryxmod.feature.bloom.BloomFeature
import io.github.orryxmod.feature.collider.ColliderData
import io.github.orryxmod.feature.collider.ColliderManager
import io.github.orryxmod.feature.collider.ColliderShape
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
                if (feature == null) {
                    sendError("Feature not found: $featureId")
                } else if (feature.enabled) {
                    sendInfo("Feature is already enabled: $featureId")
                } else if (FeatureRegistry.enable(feature)) {
                    sendSuccess("Enabled feature: $featureId")
                } else {
                    sendError("Failed to enable feature: $featureId")
                }
            }
            "disable" -> {
                if (args.size < 2) {
                    sendError("Usage: .disable <feature_id>")
                    return
                }
                val featureId = args[1]
                val feature = FeatureRegistry.get(featureId)
                if (feature == null) {
                    sendError("Feature not found: $featureId")
                } else if (!feature.enabled) {
                    sendInfo("Feature is already disabled: $featureId")
                } else if (FeatureRegistry.disable(feature)) {
                    sendSuccess("Disabled feature: $featureId")
                } else {
                    sendError("Failed to disable feature: $featureId")
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
                val indicatorStr = args.getOrNull(2) ?: "texture"
                val indicatorType = IndicatorType.fromString(indicatorStr)
                val scale = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                val maxDist = args.getOrNull(4)?.toDoubleOrNull() ?: 50.0
                val config = AimConfig(
                    scale = scale,
                    maxDistance = maxDist,
                    indicatorType = indicatorType
                )
                AimFeature.startAiming("test", module, config)
                sendSuccess("Aim started: mode=$mode, indicator=$indicatorStr")
                sendInfo("Left click = confirm, Right click/ESC = cancel")
            }
            "cancelaim" -> {
                AimFeature.cancel()
                sendSuccess("Aim cancelled")
            }
            "pressaim" -> {
                val minScale = args.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                val maxScale = args.getOrNull(2)?.toDoubleOrNull() ?: 5.0
                val maxTicks = args.getOrNull(3)?.toLongOrNull() ?: 100L
                val maxDistance = args.getOrNull(4)?.toDoubleOrNull() ?: 20.0
                if (!minScale.isFinite() || !maxScale.isFinite() || !maxDistance.isFinite() ||
                    minScale < 0.0 || maxScale < minScale || maxTicks !in 1..6_000L || maxDistance < 0.0
                ) {
                    sendError("Usage: .pressaim [minScale] [maxScale] [maxTicks] [maxDistance]")
                    return
                }
                AimState.startPressAiming(
                    skill = "test-press",
                    module = AimModule.POINT,
                    config = AimConfig(scale = minScale, maxDistance = maxDistance),
                    minScale = minScale,
                    maxScale = maxScale,
                    durationTicks = maxTicks
                )
                sendSuccess("Pressing Aim started: $minScale -> $maxScale in $maxTicks ticks")
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
                        MouseFeature.requestCursorVisible(true)
                        sendSuccess("Mouse cursor shown")
                        sendInfo("Press M or use .mouse hide to hide")
                    }
                    "hide", "off", "0" -> {
                        MouseFeature.requestCursorVisible(false)
                        sendSuccess("Mouse cursor hidden")
                    }
                    "toggle", "t" -> {
                        val show = !MouseFeature.isVisible()
                        MouseFeature.requestCursorVisible(show)
                        val state = if (show) "shown" else "hidden"
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

            // ========== 碰撞箱测试命令 ==========
            "collider" -> {
                val player = MC.player ?: return
                val sub = args.getOrNull(1)?.lowercase() ?: "help"
                val px = player.posX
                val py = player.posY
                val pz = player.posZ
                var nextId = 0
                fun genId() = "test-collider-${nextId++}-${System.currentTimeMillis()}"

                when (sub) {
                    "sphere" -> {
                        val radius = args.getOrNull(2)?.toDoubleOrNull() ?: 1.5
                        val data = ColliderData(
                            id = genId(), r = 0, g = 255, b = 0, a = 200,
                            shape = ColliderShape.Sphere(px, py + 1.0, pz, radius)
                        )
                        ColliderManager.add(data)
                        sendSuccess("Sphere collider: radius=$radius")
                    }
                    "aabb" -> {
                        val hx = args.getOrNull(2)?.toDoubleOrNull() ?: 1.0
                        val hy = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                        val hz = args.getOrNull(4)?.toDoubleOrNull() ?: 1.0
                        val data = ColliderData(
                            id = genId(), r = 255, g = 255, b = 0, a = 200,
                            shape = ColliderShape.AABB(px, py + hy, pz, hx, hy, hz)
                        )
                        ColliderManager.add(data)
                        sendSuccess("AABB collider: half=($hx, $hy, $hz)")
                    }
                    "obb" -> {
                        val hx = args.getOrNull(2)?.toDoubleOrNull() ?: 1.0
                        val hy = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                        val hz = args.getOrNull(4)?.toDoubleOrNull() ?: 1.0
                        // 使用玩家朝向生成四元数（绕 Y 轴旋转）
                        val yawRad = Math.toRadians(-player.rotationYaw.toDouble())
                        val halfYaw = yawRad / 2.0
                        val qy = kotlin.math.sin(halfYaw).toFloat()
                        val qw = kotlin.math.cos(halfYaw).toFloat()
                        val data = ColliderData(
                            id = genId(), r = 255, g = 128, b = 0, a = 200,
                            shape = ColliderShape.OBB(px, py + hy, pz, hx, hy, hz, 0f, qy, 0f, qw)
                        )
                        ColliderManager.add(data)
                        sendSuccess("OBB collider: half=($hx, $hy, $hz), yaw=${player.rotationYaw}")
                    }
                    "capsule" -> {
                        val radius = args.getOrNull(2)?.toDoubleOrNull() ?: 0.5
                        val halfHeight = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                        val data = ColliderData(
                            id = genId(), r = 0, g = 200, b = 255, a = 200,
                            shape = ColliderShape.Capsule(px, py + halfHeight + radius, pz, radius, halfHeight)
                        )
                        ColliderManager.add(data)
                        sendSuccess("Capsule collider: radius=$radius, halfHeight=$halfHeight")
                    }
                    "ray" -> {
                        val length = args.getOrNull(2)?.toDoubleOrNull() ?: 10.0
                        val lookVec = player.lookVec
                        val data = ColliderData(
                            id = genId(), r = 255, g = 0, b = 0, a = 200,
                            shape = ColliderShape.Ray(
                                px, py + player.eyeHeight.toDouble(), pz,
                                lookVec.x, lookVec.y, lookVec.z, length
                            )
                        )
                        ColliderManager.add(data)
                        sendSuccess("Ray collider: length=$length")
                    }
                    "composite" -> {
                        val children = listOf(
                            ColliderData(genId(), 0, 255, 128, 220, ColliderShape.Sphere(px - 1.5, py + 1.2, pz, 1.0)),
                            ColliderData(genId(), 255, 200, 0, 220, ColliderShape.AABB(px + 1.5, py + 1.2, pz, 0.8, 1.2, 0.8)),
                            ColliderData(genId(), 0, 180, 255, 220, ColliderShape.Capsule(px, py + 1.6, pz + 1.8, 0.5, 1.0))
                        )
                        ColliderManager.add(
                            ColliderData(genId(), 255, 255, 255, 220, ColliderShape.Composite(children))
                        )
                        sendSuccess("Composite collider: ${children.size} children")
                    }
                    "clear" -> {
                        val count = ColliderManager.size
                        ColliderManager.clear()
                        sendSuccess("Cleared $count colliders")
                    }
                    else -> {
                        sendInfo("Usage: .collider <sphere|aabb|obb|capsule|ray|composite|clear>")
                        sendInfo("  .collider sphere [radius]")
                        sendInfo("  .collider aabb [hx] [hy] [hz]")
                        sendInfo("  .collider obb [hx] [hy] [hz]")
                        sendInfo("  .collider capsule [radius] [halfHeight]")
                        sendInfo("  .collider ray [length]")
                        sendInfo("  .collider composite")
                        sendInfo("  .collider clear")
                    }
                }
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
                sendInfo("  .aim [point|dir|area] [texture|model|circle] [scale] [maxDist]")
                sendInfo("  .cancelaim / .pressaim [min] [max] [ticks] [distance]")
                sendInfo("${TextFormatting.WHITE}--- Navigation ---")
                sendInfo("  .nav [x] [y] [z] / .stopnav")
                sendInfo("${TextFormatting.WHITE}--- Mouse ---")
                sendInfo("  .mouse [show|hide|toggle]")
                sendInfo("${TextFormatting.WHITE}--- Bloom ---")
                sendInfo("  .bloom [on|off|toggle|status]")
                sendInfo("  .bloomadd <name> [r] [g] [b] [strength] [radius] [priority]")
                sendInfo("  .bloomremove <name> / .bloomclear / .bloomlist")
                sendInfo("  .bloomtest [r] [g] [b] [strength] / .bloommax [n]")
                sendInfo("${TextFormatting.WHITE}--- Collider ---")
                sendInfo("  .collider <sphere|aabb|obb|capsule|ray|composite|clear>")
            }

            else -> {
                sendError("Unknown command: $command")
                sendInfo("Type .help for commands")
            }
        }
    }
}