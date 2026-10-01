package com.assetflux.preview

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parser OBJ sederhana untuk posisi vertex, UV, dan face.
 * Normal (vn) diabaikan karena renderer saat ini tidak menggunakannya.
 */
object ObjMeshParser {

    private data class Corner(
        val positionIndex: Int,
        val uvIndex: Int
    )

    private data class VertexKey(
        val positionIndex: Int,
        val uvIndex: Int
    )

    fun parse(input: InputStream): MeshData {
        val positions = mutableListOf<FloatArray>()
        val uvs = mutableListOf<FloatArray>()
        val faces = mutableListOf<List<Corner>>()

        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            reader.forEachLine { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachLine

                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "v" -> {
                        require(parts.size >= 4) {
                            "Vertex OBJ harus memiliki koordinat X, Y, Z."
                        }
                        positions.add(
                            floatArrayOf(
                                parts[1].toFloat(),
                                parts[2].toFloat(),
                                parts[3].toFloat()
                            )
                        )
                    }

                    "vt" -> {
                        require(parts.size >= 3) {
                            "Koordinat UV OBJ tidak lengkap."
                        }
                        uvs.add(
                            floatArrayOf(
                                parts[1].toFloat(),
                                parts[2].toFloat()
                            )
                        )
                    }

                    "f" -> {
                        require(parts.size >= 4) {
                            "Face OBJ harus memiliki minimal 3 vertex."
                        }

                        val corners = parts.drop(1).map { token ->
                            val fields = token.split("/")
                            val positionIndex = resolveIndex(
                                fields[0], positions.size, "vertex"
                            )
                            val uvIndex = if (
                                fields.size > 1 && fields[1].isNotEmpty()
                            ) {
                                resolveIndex(fields[1], uvs.size, "UV")
                            } else {
                                -1
                            }

                            Corner(positionIndex, uvIndex)
                        }
                        faces.add(corners)
                    }
                }
            }
        }

        require(positions.isNotEmpty()) {
            "File OBJ tidak memiliki vertex."
        }
        require(faces.isNotEmpty()) {
            "File OBJ tidak memiliki face."
        }

        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        val vertexMap = mutableMapOf<VertexKey, Int>()

        fun getVertexIndex(corner: Corner): Int {
            val key = VertexKey(corner.positionIndex, corner.uvIndex)
            val existing = vertexMap[key]
            if (existing != null) return existing

            val newIndex = vertices.size / MeshData.FLOATS_PER_VERTEX
            require(newIndex < Short.MAX_VALUE.toInt()) {
                "Model terlalu besar untuk indeks mesh saat ini."
            }

            val position = positions[corner.positionIndex]
            val uv = if (corner.uvIndex >= 0) {
                uvs[corner.uvIndex]
            } else {
                floatArrayOf(0f, 0f)
            }

            vertices.add(position[0])
            vertices.add(position[1])
            vertices.add(position[2])
            vertices.add(uv[0])
            vertices.add(uv[1])

            vertexMap[key] = newIndex
            return newIndex
        }

        for (face in faces) {
            // Triangulasi kipas: (0,1,2), (0,2,3), dan seterusnya.
            for (i in 1 until face.size - 1) {
                val triangle = listOf(face[0], face[i], face[i + 1])
                for (corner in triangle) {
                    indices.add(getVertexIndex(corner).toShort())
                }
            }
        }

        normalizePositions(vertices)

        return MeshData(
            vertices = vertices.toFloatArray(),
            indices = indices.toShortArray()
        )
    }

    private fun resolveIndex(
        value: String,
        count: Int,
        label: String
    ): Int {
        val objIndex = value.toIntOrNull()
            ?: throw IllegalArgumentException("Indeks $label tidak valid: $value")

        require(objIndex != 0) { "Indeks OBJ tidak boleh nol." }

        val index = if (objIndex > 0) objIndex - 1 else count + objIndex
        require(index in 0 until count) {
            "Indeks $label di luar rentang: $objIndex"
        }
        return index
    }

    private fun normalizePositions(vertices: MutableList<Float>) {
        val stride = MeshData.FLOATS_PER_VERTEX
        val count = vertices.size / stride

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (i in 0 until count) {
            val base = i * stride
            val x = vertices[base]
            val y = vertices[base + 1]
            val z = vertices[base + 2]

            require(x.isFinite() && y.isFinite() && z.isFinite()) {
                "Model memiliki koordinat yang tidak valid."
            }

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
        }

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val centerZ = (minZ + maxZ) / 2f
        val maxExtent = maxOf(maxX - minX, maxY - minY, maxZ - minZ)

        require(maxExtent > 0f && maxExtent.isFinite()) {
            "Ukuran model OBJ tidak valid."
        }

        val scale = 2f / maxExtent

        for (i in 0 until count) {
            val base = i * stride
            vertices[base] = (vertices[base] - centerX) * scale
            vertices[base + 1] = (vertices[base + 1] - centerY) * scale
            vertices[base + 2] = (vertices[base + 2] - centerZ) * scale
        }
    }
}
