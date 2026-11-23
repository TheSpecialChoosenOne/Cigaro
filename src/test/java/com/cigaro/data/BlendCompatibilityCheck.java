package com.cigaro.data;

public final class BlendCompatibilityCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        BlendData old = BlendData.deserialize("VANILLA:COFFEE:0.6");
        require(old != null && old.getPrimary() == CigaretteType.VANILLA && old.getPrimaryTobacco() == null, "old blend");
        BlendData current = new BlendData(CigaretteType.HERBAL, CigaretteType.COFFEE, 0.625, TobaccoType.ANCIENT, TobaccoType.BURLEY);
        BlendData read = BlendData.deserialize(current.serialize());
        require(read != null && read.getPrimaryTobacco() == TobaccoType.ANCIENT && read.getSecondaryTobacco() == TobaccoType.BURLEY && read.getPrimaryRatio() == 0.625, "new blend");
        require(BlendData.deserialize("VANILLA:COFFEE:NaN") == null, "non-finite ratio");
        PipeData pipe = new PipeData(20, 50); pipe.loadBlend(current, 5); pipe.puff();
        PipeData copy = PipeData.deserialize(pipe.serialize());
        require(copy != null && copy.getBlendData().getPrimaryTobacco() == TobaccoType.ANCIENT && copy.getChargesLeft() == 4, "pipe round trip");
        PipeData legacy = PipeData.deserialize("BLEND:VANILLA:COFFEE:0.6:4:5:20:50");
        require(legacy != null && legacy.getBlendData() != null && legacy.getChargesLeft() == 4, "old pipe");
        HookahData hookah = HookahData.deserialize("1:3:BLEND:" + current.serialize());
        HookahData restored = HookahData.deserialize(hookah.serialize());
        require(restored.getBlendData() != null && restored.getBlendData().getPrimaryTobacco() == TobaccoType.ANCIENT, "hookah round trip");
    }
}
