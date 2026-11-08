
package com.ying.fabric.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.xiyu.onekeyminer.api.OneKeyMinerAPI;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public final class ExampleModFabricClient implements ClientModInitializer {

    /**
     * 穿墙线框 Pipeline。
     */
    private static final RenderPipeline THROUGH_WALLS =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(
                                    "onekeyminerplus",
                                    "pipeline/preview_outline"
                            ))
                            .withColorTargetState(ColorTargetState.DEFAULT)
                            .withDepthStencilState(Optional.empty())
                            .build()
            );

    /**
     * 顶点缓冲区。
     */
    private static final StagedVertexBuffer STAGED_BUFFER =
            new StagedVertexBuffer(
                    () -> "OneKeyMinerPlus Preview Outline",
                    RenderType.SMALL_BUFFER_SIZE
            );

    private static final int COLOR = 0xFFFFFFFF;

    private static final float LINE_WIDTH = 2.0F;

    @Override
    public void onInitializeClient() {
        LevelRenderEvents.END_MAIN.register(
                ExampleModFabricClient::renderPreview
        );
    }

    private static void renderPreview(
            net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context
    ) {
        try {
            List<BlockPos> blocks = OneKeyMinerAPI.getPreviewBlocks();

            if (blocks == null || blocks.isEmpty()) {
                return;
            }

            Minecraft client = Minecraft.getInstance();

            if (client.level == null || client.gameRenderer == null) {
                return;
            }

            var vertexFormat =
                    DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH;

            if (vertexFormat == null) {
                return;
            }

            StagedVertexBuffer.Draw draw =
                    STAGED_BUFFER.appendDraw(
                            vertexFormat,
                            THROUGH_WALLS.getPrimitiveTopology()
                    );

            VertexConsumer builder =
                    STAGED_BUFFER.getVertexBuilder(draw);

            Vec3 camera = context.levelState()
                    .cameraRenderState.pos;

            PoseStack poseStack = context.poseStack();

            poseStack.pushPose();

            try {
                // 世界坐标转换为相机相对坐标。
                poseStack.translate(
                        -camera.x,
                        -camera.y,
                        -camera.z
                );

                Matrix4f matrix = poseStack.last().pose();

                // 生成整个方块集合的外轮廓。
                drawUnionOutline(builder, matrix, blocks);

            } finally {
                poseStack.popPose();
            }

            STAGED_BUFFER.upload();

            StagedVertexBuffer.ExecuteInfo info =
                    STAGED_BUFFER.getExecuteInfo(draw);

            if (info == null) {
                return;
            }

            CompiledRenderPipeline compiledPipeline =
                    RenderSystem.getCompiledPipelineNullable(
                            THROUGH_WALLS
                    );

            if (compiledPipeline == null) {
                return;
            }

            drawBuffer(client, info, compiledPipeline);

        } finally {
            STAGED_BUFFER.endFrame();
        }
    }

    /**
     * 绘制方块集合的整体外轮廓。
     *
     * 1. 找出所有没有相邻方块的外露面。
     * 2. 提取外露面的四条边。
     * 3. 去掉共面相邻面之间的拼接线。
     * 4. 去掉重复边。
     */
    private static void drawUnionOutline(
            VertexConsumer builder,
            Matrix4f matrix,
            List<BlockPos> blocks
    ) {
        Set<BlockPos> blockSet = new HashSet<>(blocks);

        /*
         * 每条边对应不同方向的外露面数量。
         *
         * 同一条边：
         * 两个相同方向的面相接，说明是共面拼接线，不绘制。
         * 不同方向的面相接，说明是形状转折边，需要绘制。
         */
        Map<EdgeKey, EnumMap<Direction, Integer>> edges =
                new HashMap<>();

        for (BlockPos pos : blockSet) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            for (Direction direction : Direction.values()) {

                // 邻居也在集合中，当前面属于内部面。
                if (blockSet.contains(pos.relative(direction))) {
                    continue;
                }

                // 当前面暴露在集合外部，提取它的四条边。
                int[][] corners = getFaceCorners(
                        direction,
                        x, y, z
                );

                addFaceEdges(edges, direction, corners);
            }
        }

        // 最终绘制边界线。
        for (Map.Entry<EdgeKey, EnumMap<Direction, Integer>> entry
                : edges.entrySet()) {

            EdgeKey edge = entry.getKey();
            EnumMap<Direction, Integer> faceCounts = entry.getValue();

            int totalFaces = 0;

            for (int count : faceCounts.values()) {
                totalFaces += count;
            }

            /*
             * 两个外露面朝向相同：
             * 说明它们是两个相邻的共面方块表面。
             * 中间的拼接线不需要绘制。
             */
            if (totalFaces == 2 && faceCounts.size() == 1) {
                continue;
            }

            emitEdge(
                    builder,
                    matrix,
                    edge.x1(), edge.y1(), edge.z1(),
                    edge.x2(), edge.y2(), edge.z2()
            );
        }
    }

    /**
     * 获取方块指定面的四个顶点。
     *
     * 坐标均为世界整数坐标。
     */
    private static int[][] getFaceCorners(
            Direction direction,
            int x,
            int y,
            int z
    ) {
        return switch (direction) {

            // 下表面
            case DOWN -> new int[][]{
                    {x, y, z},
                    {x + 1, y, z},
                    {x + 1, y, z + 1},
                    {x, y, z + 1}
            };

            // 上表面
            case UP -> new int[][]{
                    {x, y + 1, z},
                    {x + 1, y + 1, z},
                    {x + 1, y + 1, z + 1},
                    {x, y + 1, z + 1}
            };

            // 北面
            case NORTH -> new int[][]{
                    {x, y, z},
                    {x + 1, y, z},
                    {x + 1, y + 1, z},
                    {x, y + 1, z}
            };

            // 南面
            case SOUTH -> new int[][]{
                    {x, y, z + 1},
                    {x + 1, y, z + 1},
                    {x + 1, y + 1, z + 1},
                    {x, y + 1, z + 1}
            };

            // 西面
            case WEST -> new int[][]{
                    {x, y, z},
                    {x, y, z + 1},
                    {x, y + 1, z + 1},
                    {x, y + 1, z}
            };

            // 东面
            case EAST -> new int[][]{
                    {x + 1, y, z},
                    {x + 1, y, z + 1},
                    {x + 1, y + 1, z + 1},
                    {x + 1, y + 1, z}
            };
        };
    }

    /**
     * 将一个外露面的四条边加入集合。
     */
    private static void addFaceEdges(
            Map<EdgeKey, EnumMap<Direction, Integer>> edges,
            Direction faceDirection,
            int[][] corners
    ) {
        for (int i = 0; i < 4; i++) {
            int[] a = corners[i];
            int[] b = corners[(i + 1) % 4];

            EdgeKey key = new EdgeKey(
                    a[0], a[1], a[2],
                    b[0], b[1], b[2]
            );

            EnumMap<Direction, Integer> faceCounts =
                    edges.computeIfAbsent(
                            key,
                            k -> new EnumMap<>(Direction.class)
                    );

            faceCounts.merge(faceDirection, 1, Integer::sum);
        }
    }

    /**
     * 无方向边。
     *
     * 无论以哪个端点作为起点，都会得到相同的 Key。
     */
    private record EdgeKey(
            int x1, int y1, int z1,
            int x2, int y2, int z2
    ) {
        private EdgeKey {
            if (comparePoint(
                    x1, y1, z1,
                    x2, y2, z2
            ) > 0) {
                int tx = x1;
                int ty = y1;
                int tz = z1;

                x1 = x2;
                y1 = y2;
                z1 = z2;

                x2 = tx;
                y2 = ty;
                z2 = tz;
            }
        }

        private static int comparePoint(
                int ax, int ay, int az,
                int bx, int by, int bz
        ) {
            int result = Integer.compare(ax, bx);

            if (result != 0) return result;

            result = Integer.compare(ay, by);

            if (result != 0) return result;

            return Integer.compare(az, bz);
        }
    }

    /**
     * 添加一条线段。
     *
     * 保留你原来已经验证成功的顶点写入方式。
     */
    private static void emitEdge(
            VertexConsumer builder,
            Matrix4f matrix,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz
    ) {
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;

        builder.addVertex(matrix, ax, ay, az)
                .setColor(COLOR)
                .setNormal(nx, ny, nz)
                .setLineWidth(LINE_WIDTH);

        builder.addVertex(matrix, bx, by, bz)
                .setColor(COLOR)
                .setNormal(nx, ny, nz)
                .setLineWidth(LINE_WIDTH);
    }

    /**
     * GPU 绘制。
     * 保留原来的实现。
     */
    private static void drawBuffer(
            Minecraft client,
            StagedVertexBuffer.ExecuteInfo info,
            CompiledRenderPipeline pipeline
    ) {
        RenderTarget mainTarget =
                client.gameRenderer.mainRenderTarget();

        var colorTexture = mainTarget.getColorTextureView();

        if (colorTexture == null) {
            return;
        }

        GpuBufferSlice dynamicTransforms =
                RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrixCopy(),
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(),
                        new Matrix4f()
                );

        try (RenderPass renderPass =
                     RenderSystem.getDevice()
                             .createCommandEncoder()
                             .createRenderPass(
                                     () -> "OneKeyMinerPlus Preview Outline",
                                     colorTexture,
                                     Optional.empty(),
                                     mainTarget.getDepthTextureView(),
                                     OptionalDouble.empty()
                             )
        ) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);

            renderPass.setUniform(
                    "DynamicTransforms",
                    dynamicTransforms
            );

            renderPass.setVertexBuffer(
                    0,
                    info.vertexBuffer().slice()
            );

            renderPass.setIndexBuffer(
                    info.indexBuffer(),
                    info.indexType()
            );

            renderPass.drawIndexed(
                    info.indexCount(),
                    1,
                    info.firstIndex(),
                    info.baseVertex(),
                    0
            );
        }
    }
}
