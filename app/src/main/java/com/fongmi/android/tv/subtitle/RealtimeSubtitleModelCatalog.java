package com.fongmi.android.tv.subtitle;

final class RealtimeSubtitleModelCatalog {

    enum Engine {
        ONLINE_TRANSDUCER,
        OFFLINE_WENET_CTC,
        OFFLINE_MOONSHINE
    }

    record ModelFile(String relativePath, String url, long size, String sha256) {
    }

    record ModelSpec(String id, String directory, Engine engine, int minRamMb, boolean needsVad, ModelFile[] files) {
    }

    private static final ModelFile VAD = new ModelFile(
            "silero_vad.onnx",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            643854L,
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6");

    private static final ModelSpec[] MODELS = {
            new ModelSpec("zh", "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", Engine.ONLINE_TRANSDUCER, 768, false, new ModelFile[]{
                    file("encoder-epoch-99-avg-1.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", "204ad334e2e683fd295359930cc16fc0432a23ac", 21621684L, "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1"),
                    file("decoder-epoch-99-avg-1.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", "204ad334e2e683fd295359930cc16fc0432a23ac", 7509745L, "5ee0f03a2768ff1d5c83ef3a493243c7935d316cd41280037b14783a3467cc78"),
                    file("joiner-epoch-99-avg-1.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", "204ad334e2e683fd295359930cc16fc0432a23ac", 1795562L, "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", "204ad334e2e683fd295359930cc16fc0432a23ac", 48697L, "8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac"),
            }),
            new ModelSpec("yue", "sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10", Engine.OFFLINE_WENET_CTC, 1280, true, new ModelFile[]{
                    file("model.int8.onnx", "csukuangfj/sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10", "c911764e8227cf372ee60adc75f7f407e5b8c905", 134698500L, "201bfd9e12ec4ac9ee3b23c5e071d9fa2381a8b21df317e2e08a170d6f1f55d3"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10", "c911764e8227cf372ee60adc75f7f407e5b8c905", 85361L, "c7750677a1183606d2fd6f16d792e06e70d9843dba8a0c6e23a9dec78e06977a"),
            }),
            new ModelSpec("en", "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", Engine.ONLINE_TRANSDUCER, 768, false, new ModelFile[]{
                    file("encoder-epoch-99-avg-1.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", "d42f2d9f7ca24806fb667456a18a9f1b60f70d16", 42845182L, "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3"),
                    file("decoder-epoch-99-avg-1.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", "d42f2d9f7ca24806fb667456a18a9f1b60f70d16", 2092272L, "45a7f940ecfb53d89fa270ad11b88b961e53a317203eb24b1c8e95ed208b0f30"),
                    file("joiner-epoch-99-avg-1.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", "d42f2d9f7ca24806fb667456a18a9f1b60f70d16", 259572L, "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", "d42f2d9f7ca24806fb667456a18a9f1b60f70d16", 5048L, "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"),
            }),
            new ModelSpec("de", "sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06", Engine.ONLINE_TRANSDUCER, 1024, false, new ModelFile[]{
                    file("encoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06", "887db3d083240198c2d2b99fb66cfcfe6948ced8", 70091557L, "6e83993d6967ec7a3498b055b7e85ace85b5d64d1b1e8773cb29a43a11f5edb5"),
                    file("decoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06", "887db3d083240198c2d2b99fb66cfcfe6948ced8", 617489L, "94a29592b403c53fa2231b478637da1ab4abcef7f5e46e432098416a4a3ed562"),
                    file("joiner.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06", "887db3d083240198c2d2b99fb66cfcfe6948ced8", 336817L, "28356bff070aea51ab1d725a3278e81d19f9300f860d3248a7014292264df15a"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06", "887db3d083240198c2d2b99fb66cfcfe6948ced8", 5606L, "86e8370994ff2c01149ba8c4f8709aa93cdc18914b27a717e291e96faf39a6eb"),
            }),
            new ModelSpec("fr", "sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06", Engine.ONLINE_TRANSDUCER, 1024, false, new ModelFile[]{
                    file("encoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06", "08b84b7b7cf519be9817e9c16919d96a7a8bad91", 70092599L, "e02facae1daf6f1f13da67ea3ace7c722516d0868d1768d78c0580bc22cc0c5b"),
                    file("decoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06", "08b84b7b7cf519be9817e9c16919d96a7a8bad91", 617488L, "6aed547570e3ab5afc05429a017cedd3a056c16df3baa5703f02461cefa25bac"),
                    file("joiner.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06", "08b84b7b7cf519be9817e9c16919d96a7a8bad91", 336817L, "a51eec759bcdcaae2614686fa2a8b57417b2d420dd55a5a5558b388d35a9b2b6"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06", "08b84b7b7cf519be9817e9c16919d96a7a8bad91", 5415L, "fedfb9c844bfb2bf14171f8184863e3d617b815a8667bdd9fc9a3149fde73298"),
            }),
            new ModelSpec("es", "sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06", Engine.ONLINE_TRANSDUCER, 1280, false, new ModelFile[]{
                    file("encoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06", "20cf7a4921613397841d31168796cade5b866585", 154878102L, "2d9f5ef87d1a5257f8a6687e21501c56f3aa2fcbfcfab9364dcc4ce4e06ae81b"),
                    file("decoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06", "20cf7a4921613397841d31168796cade5b866585", 617488L, "d4ce176b94b25f7acc88717bc3f704fcf5d6e131aaac2e0cabab3885541181ee"),
                    file("joiner.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06", "20cf7a4921613397841d31168796cade5b866585", 336817L, "dae35df88d676e320fcdb99217328e66dcf722bf11b0f2459e14ddb5b982ded5"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06", "20cf7a4921613397841d31168796cade5b866585", 6385L, "1be5e0a58e05d06d327df4c6b7b5e4f8aba01da6981eb016fcaceafc6a56680f"),
            }),
            new ModelSpec("ja", "sherpa-onnx-moonshine-tiny-ja-quantized-2026-02-27", Engine.OFFLINE_MOONSHINE, 1280, true, new ModelFile[]{
                    file("encoder_model.ort", "csukuangfj2/sherpa-onnx-moonshine-tiny-ja-quantized-2026-02-27", "550e2eb0a8b33092f3b394f64649ee1b3d9eb506", 13238184L, "86ece73812604b9b5f1274b4d1e6eec0d783b96088ff46d49e30a53f881cad73"),
                    file("decoder_model_merged.ort", "csukuangfj2/sherpa-onnx-moonshine-tiny-ja-quantized-2026-02-27", "550e2eb0a8b33092f3b394f64649ee1b3d9eb506", 58327272L, "9fcd9b71323a496b307e20dd305c4e9a1b533c7bedd6e4f660e974967dd60bb6"),
                    file("tokens.txt", "csukuangfj2/sherpa-onnx-moonshine-tiny-ja-quantized-2026-02-27", "550e2eb0a8b33092f3b394f64649ee1b3d9eb506", 549350L, "2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049"),
            }),
            new ModelSpec("zh-en", "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20", Engine.ONLINE_TRANSDUCER, 1280, false, new ModelFile[]{
                    file("encoder-epoch-99-avg-1.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20", "98590b7ed6443e77b714204da2757d75e1a642f4", 181895032L, "8fa764187a261844f859d7143ebaa563af5d10adfece4c18a8f414c88cba2a9b"),
                    file("decoder-epoch-99-avg-1.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20", "98590b7ed6443e77b714204da2757d75e1a642f4", 13876452L, "2e3b5ec371f8899ee6acd829fd753ba45772df57a91bdf37cde3136354e7db7d"),
                    file("joiner-epoch-99-avg-1.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20", "98590b7ed6443e77b714204da2757d75e1a642f4", 3228404L, "1ed689c5ed19dbaa725d9d191bb4822b5f4855a39e1ffd28cbc1f340d25b2ee0"),
                    file("tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20", "98590b7ed6443e77b714204da2757d75e1a642f4", 56317L, "a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3"),
            }),
    };

    private RealtimeSubtitleModelCatalog() {
    }

    static ModelSpec[] models() {
        return MODELS.clone();
    }

    static ModelSpec find(String id) {
        for (ModelSpec model : MODELS) if (model.id().equals(id)) return model;
        return MODELS[0];
    }

    static ModelFile vad() {
        return VAD;
    }

    static ModelFile[] downloads(ModelSpec model) {
        if (!model.needsVad()) return model.files().clone();
        ModelFile[] files = new ModelFile[model.files().length + 1];
        files[0] = VAD;
        System.arraycopy(model.files(), 0, files, 1, model.files().length);
        return files;
    }

    private static ModelFile file(String path, String repo, String revision, long size, String sha256) {
        return new ModelFile(path, "https://huggingface.co/" + repo + "/resolve/" + revision + "/" + path, size, sha256);
    }
}
