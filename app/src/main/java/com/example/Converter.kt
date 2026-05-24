package com.example

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.graphics.Bitmap
import android.graphics.Color

class ConverterConfig(
    val yOffset: Int = 32,
    val xOffset: Int = 0,
    val zOffset: Int = 0,
    val seed: Long? = null,
    val fillBlock: Int = 1,
    val repopulate: Boolean = false,
    val extraRadius: Int = 0,
    val generatorType: String = "flat"
)

data class PreviewData(val bitmap: Bitmap)

object Converter {
    fun convertToZip(
        mclevelStream: InputStream,
        zipOutputStream: OutputStream,
        config: ConverterConfig,
        progressCallback: (String) -> Unit
    ): PreviewData {
        progressCallback("Loading .mclevel file...")
        val rootTag = NbtIo.read(mclevelStream)?.second ?: throw Exception("Failed to load or parse .mclevel")
        val about = rootTag.getCompound("About")
        val environment = rootTag.getCompound("Environment")
        val map = rootTag.getCompound("Map")
        val entities = rootTag.getList("Entities")
        val tileEntities = rootTag.getList("TileEntities")

        val chunkEntities = mutableMapOf<Pair<Int, Int>, MutableList<NbtTag>>()
        val chunkTileEntities = mutableMapOf<Pair<Int, Int>, MutableList<NbtTag>>()

        progressCallback("Processing entities...")
        var player: NbtCompound? = null
        for (e in entities.value) {
            val comp = e as NbtCompound
            val id = (comp.value["id"] as? NbtString)?.value
            if (id == "LocalPlayer") {
                player = comp
                continue
            }

            val posList = comp.getList("Pos")
            if (posList.value.size == 3) {
                val posX = (posList.value[0] as NbtDouble).value + config.xOffset
                val posY = (posList.value[1] as NbtDouble).value + config.yOffset
                val posZ = (posList.value[2] as NbtDouble).value + config.zOffset

                posList.value[0] = NbtDouble(posX)
                posList.value[1] = NbtDouble(posY)
                posList.value[2] = NbtDouble(posZ)

                val cX = kotlin.math.floor(posX / 16.0).toInt()
                val cZ = kotlin.math.floor(posZ / 16.0).toInt()
                chunkEntities.getOrPut(cX to cZ) { mutableListOf() }.add(comp)
            }

            if ("TileX" in comp.value) {
                comp.value["TileX"] = NbtInt(comp.getInt("TileX") + config.xOffset)
                comp.value["TileY"] = NbtInt(comp.getInt("TileY") + config.yOffset)
                comp.value["TileZ"] = NbtInt(comp.getInt("TileZ") + config.zOffset)
            }
            if ("xTile" in comp.value) {
                comp.value["xTile"] = NbtInt(comp.getInt("xTile") + config.xOffset)
                comp.value["yTile"] = NbtInt(comp.getInt("yTile") + config.yOffset)
                comp.value["zTile"] = NbtInt(comp.getInt("zTile") + config.zOffset)
            }
        }

        progressCallback("Processing tile entities...")
        for (te in tileEntities.value) {
            val comp = te as NbtCompound
            val packedPos = comp.getInt("Pos")
            val x = (packedPos % 1024) + config.xOffset
            val y = ((packedPos shr 10) % 1024) + config.yOffset
            val z = ((packedPos shr 20) % 1024) + config.zOffset

            comp.value.remove("Pos")
            comp.value["x"] = NbtInt(x)
            comp.value["y"] = NbtInt(y)
            comp.value["z"] = NbtInt(z)

            val cX = kotlin.math.floor(x / 16.0).toInt()
            val cZ = kotlin.math.floor(z / 16.0).toInt()
            chunkTileEntities.getOrPut(cX to cZ) { mutableListOf() }.add(comp)
        }

        val spawn = map.getList("Spawn")
        val spawnX = if (spawn.value.isNotEmpty()) (spawn.value[0] as NbtShort).value.toInt() + config.xOffset else 0
        val spawnY = if (spawn.value.size > 1) (spawn.value[1] as NbtShort).value.toInt() + config.yOffset else 0
        val spawnZ = if (spawn.value.size > 2) (spawn.value[2] as NbtShort).value.toInt() + config.zOffset else 0

        val playerData = NbtCompound()
        player?.let { p ->
            playerData.value["Inventory"] = p.getList("Inventory")
            playerData.value["Motion"] = p.getList("Motion")
            playerData.value["Pos"] = p.getList("Pos")
            playerData.value["Rotation"] = p.getList("Rotation")
            playerData.value["Air"] = p.value["Air"] ?: NbtShort(300)
            playerData.value["AttackTime"] = p.value["AttackTime"] ?: NbtShort(0)
            playerData.value["DeathTime"] = p.value["DeathTime"] ?: NbtShort(0)
            playerData.value["FallDistance"] = p.value["FallDistance"] ?: NbtFloat(0f)
            playerData.value["Fire"] = p.value["Fire"] ?: NbtShort(-20)
            playerData.value["Health"] = p.value["Health"] ?: NbtShort(10)
            playerData.value["HurtTime"] = p.value["HurtTime"] ?: NbtShort(0)
            playerData.value["OnGround"] = NbtByte(1)
            playerData.value["Score"] = p.value["Score"] ?: NbtInt(0)
        }

        val levelTagData = NbtCompound()
        levelTagData.value["Player"] = playerData
        levelTagData.value["LastPlayed"] = NbtLong(about.getLong("CreatedOn"))
        levelTagData.value["SpawnX"] = NbtInt(spawnX)
        levelTagData.value["SpawnY"] = NbtInt(spawnY)
        levelTagData.value["SpawnZ"] = NbtInt(spawnZ)
        levelTagData.value["Time"] = if ("TimeOfDay" in environment.value) NbtLong(environment.getLong("TimeOfDay")) else NbtLong(0)
        config.seed?.let { levelTagData.value["RandomSeed"] = NbtLong(it) }

        val levelDat = NbtCompound()
        levelDat.value["Data"] = levelTagData

        val zout = ZipOutputStream(zipOutputStream)
        zout.putNextEntry(ZipEntry("World/level.dat"))
        NbtIo.write("", levelDat, zout)
        zout.closeEntry()

        val mapHeight = map.getShort("Height").toInt()
        val mapLength = map.getShort("Length").toInt()
        val mapWidth = map.getShort("Width").toInt()
        val mapBlocks = map.getByteArray("Blocks")
        val mapData = map.getByteArray("Data")

        val xChunks = mapLength / 16
        val zChunks = mapWidth / 16
        val startZ = -config.extraRadius
        val endZ = xChunks + config.extraRadius
        val startX = -config.extraRadius
        val endX = zChunks + config.extraRadius
        
        val totalChunks = (endZ - startZ) * (endX - startX)
        var currentChunk = 0

        val noise = SimpleNoise(config.seed ?: 12345L)
        
        val previewWidth = (endX - startX) * 16
        val previewHeight = (endZ - startZ) * 16
        val previewPixels = IntArray(previewWidth * previewHeight)
        
        fun getBlockColor(id: Byte): Int {
            return when (id.toInt() and 0xFF) {
                1 -> Color.rgb(125, 125, 125) // Stone
                2 -> Color.rgb(100, 180, 80) // Grass
                3 -> Color.rgb(120, 80, 50) // Dirt
                4 -> Color.rgb(100, 100, 100) // Cobble
                5 -> Color.rgb(140, 110, 70) // Wood P
                8, 9 -> Color.rgb(40, 60, 200) // Water
                10, 11 -> Color.rgb(220, 100, 20) // Lava
                12 -> Color.rgb(200, 180, 140) // Sand
                13 -> Color.rgb(130, 130, 130) // Gravel
                17 -> Color.rgb(90, 60, 30) // Log
                18 -> Color.rgb(60, 140, 40) // Leaves
                else -> Color.rgb(20, 20, 20) // Unknown
            }
        }

        for (rawZ in startZ until endZ) {
            for (rawX in startX until endX) {
                val convertedX = rawX + config.xOffset / 16
                val convertedZ = rawZ + config.zOffset / 16

                val isInsideMap = rawZ in 0 until xChunks && rawX in 0 until zChunks

                val blocks = ByteArray(16 * 16 * 128)
                var iBlock = 0
                for (x in rawX * 16 until rawX * 16 + 16) {
                    for (z in rawZ * 16 until rawZ * 16 + 16) {
                        if (isInsideMap) {
                            for (y in 0 until config.yOffset) {
                                if (iBlock < blocks.size) blocks[iBlock++] = config.fillBlock.toByte()
                            }
                            for (y in 0 until mapHeight) {
                                val mapIndex = (y * mapLength + z) * mapWidth + x
                                if (iBlock < blocks.size) {
                                    blocks[iBlock++] = if (mapIndex >= 0 && mapIndex < mapBlocks.size) mapBlocks[mapIndex] else 0
                                }
                            }
                            for (y in mapHeight + config.yOffset until 128) {
                                if (iBlock < blocks.size) iBlock++
                            }
                        } else {
                            if (config.generatorType == "perlin") {
                                val absX = rawX * 16 + x
                                val absZ = rawZ * 16 + z
                                val nx = absX * 0.05
                                val nz = absZ * 0.05
                                val noiseVal = noise.noise(nx, nz) * 10
                                val h = (config.yOffset + noiseVal).toInt().coerceIn(1, 127)
                                for (y in 0 until 128) {
                                    if (iBlock < blocks.size) {
                                        blocks[iBlock++] = when {
                                            y < h - 3 -> 1.toByte() // Stone
                                            y < h -> 3.toByte() // Dirt
                                            y == h -> 2.toByte() // Grass
                                            y <= config.yOffset - 2 -> 9.toByte() // Water
                                            else -> 0.toByte()
                                        }
                                    }
                                }
                            } else {
                                val h = config.yOffset
                                for (y in 0 until 128) {
                                    if (iBlock < blocks.size) {
                                        blocks[iBlock++] = when {
                                            y < h - 1 -> config.fillBlock.toByte()
                                            y == h - 1 -> (if (config.fillBlock == 1) 2 else config.fillBlock).toByte()
                                            else -> 0.toByte()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val data = ByteArray(16 * 16 * 64)
                val light = ByteArray(16 * 16 * 64)
                var iData = 0

                for (x in rawX * 16 until rawX * 16 + 16) {
                    for (z in rawZ * 16 until rawZ * 16 + 16) {
                        if (isInsideMap) {
                            for (y in 0 until config.yOffset step 2) iData++
                            for (y in 0 until mapHeight step 2) {
                                val idx1 = (y * mapLength + z) * mapWidth + x
                                val idx2 = ((y + 1) * mapLength + z) * mapWidth + x
    
                                val block1Data = if (idx1 >= 0 && idx1 < mapData.size) mapData[idx1].toInt() and 0xFF else 0
                                val block2Data = if (idx2 >= 0 && idx2 < mapData.size) mapData[idx2].toInt() and 0xFF else 0
    
                                val meta1 = block1Data shr 4
                                val meta2 = block2Data shr 4
                                val light1 = block1Data and 0xF
                                val light2 = block2Data and 0xF
    
                                val dataByte = (meta2 shl 4) or meta1
                                val lightByte = (light2 shl 4) or light1
    
                                if (iData < data.size) {
                                    data[iData] = dataByte.toByte()
                                    light[iData] = lightByte.toByte()
                                }
                                iData++
                            }
                            for (y in mapHeight + config.yOffset until 128 step 2) iData++
                        } else {
                            iData += 64
                        }
                    }
                }

                val heightMap = ByteArray(256)
                for (x in 0 until 16) {
                    for (z in 0 until 16) {
                        var highest = 0
                        var colorPixel = Color.rgb(20, 20, 20)
                        for (y in 127 downTo 0) {
                            val block = blocks[x * 2048 + z * 128 + y]
                            val bInt = block.toInt() and 0xFF
                            if (bInt != 0 && bInt != 6 && bInt != 20 && bInt != 37 && bInt != 38 && bInt != 39 && bInt != 40 && bInt != 50 && bInt != 51) {
                                highest = y
                                colorPixel = getBlockColor(block)
                                break
                            }
                        }
                        heightMap[z + x * 16] = (highest + 1).toByte()
                        // X mapped to Z here? Wait, mapWidth corresponds to Z axis?
                        // Let's look at index
                        val pxX = (rawZ - startZ) * 16 + z
                        val pxY = (rawX - startX) * 16 + x
                        if (pxX in 0 until previewWidth && pxY in 0 until previewHeight) {
                            previewPixels[pxY * previewWidth + pxX] = colorPixel
                        }
                    }
                }

                val chunkLevel = NbtCompound()
                chunkLevel.value["Entities"] = NbtList(10, chunkEntities[convertedX to convertedZ] ?: mutableListOf())
                chunkLevel.value["TileEntities"] = NbtList(10, chunkTileEntities[convertedX to convertedZ] ?: mutableListOf())
                chunkLevel.value["LastUpdate"] = NbtLong(200)
                chunkLevel.value["xPos"] = NbtInt(convertedX)
                chunkLevel.value["zPos"] = NbtInt(convertedZ)
                chunkLevel.value["Blocks"] = NbtByteArray(blocks)
                chunkLevel.value["Data"] = NbtByteArray(data)
                chunkLevel.value["BlockLight"] = NbtByteArray(light)
                chunkLevel.value["SkyLight"] = NbtByteArray(ByteArray(16 * 16 * 64))
                chunkLevel.value["HeightMap"] = NbtByteArray(heightMap)
                chunkLevel.value["TerrainPopulated"] = NbtByte(if (config.repopulate) 0 else 1)

                val chunkRoot = NbtCompound()
                chunkRoot.value["Level"] = chunkLevel

                val modX = ((convertedX % 64) + 64) % 64
                val modZ = ((convertedZ % 64) + 64) % 64
                val chunkPath = "World/${modX.toString(36)}/${modZ.toString(36)}/c.${convertedX.toString(36)}.${convertedZ.toString(36)}.dat"

                zout.putNextEntry(ZipEntry(chunkPath))
                NbtIo.write("", chunkRoot, zout)
                zout.closeEntry()

                currentChunk++
                if (currentChunk % 16 == 0 || currentChunk == totalChunks) {
                    progressCallback("Writing chunks ($currentChunk / $totalChunks)...")
                }
            }
        }

        zout.flush()
        zout.close()
        progressCallback("Completed successfully!")
        
        val bitmap = Bitmap.createBitmap(previewPixels, previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        return PreviewData(bitmap)
    }
}
