package com.cigaro.data;

public class PipeData {

    private CigaretteType loadedType;
    private BlendData blendData;

    private int chargesLeft;
    private int maxCharges;
    private int durability;
    private int maxDurability;

    public static final int MAX_CHARGES = 5;
    public static final int DEFAULT_MAX_DURABILITY = 50;

    public PipeData(int durability, int maxDurability) {
        this.loadedType = null;
        this.blendData = null;
        this.chargesLeft = 0;
        this.maxCharges = 0;
        this.durability = durability;
        this.maxDurability = maxDurability;
    }

    public void load(CigaretteType type, int charges) {
        this.loadedType = type;
        this.blendData = null;
        this.chargesLeft = charges;
        this.maxCharges = charges;
    }

    public void loadBlend(BlendData blend, int charges) {
        this.loadedType = null;
        this.blendData = blend;
        this.chargesLeft = charges;
        this.maxCharges = charges;
    }

    public boolean isEmpty() {
        return chargesLeft <= 0;
    }

    public boolean isLoaded() {
        return loadedType != null || blendData != null;
    }

    public void puff() {
        if (chargesLeft > 0) chargesLeft--;
        if (chargesLeft <= 0) {
            loadedType = null;
            blendData = null;
        }
    }

    public void useDurability() {
        durability--;
    }

    public boolean isBroken() {
        return durability <= 0;
    }

    public CigaretteType getLoadedType() { return loadedType; }
    public BlendData getBlendData() { return blendData; }

    public int getChargesLeft() { return chargesLeft; }
    public int getMaxCharges() { return maxCharges; }
    public int getDurability() { return durability; }
    public int getMaxDurability() { return maxDurability; }

    public boolean isBlend() { return blendData != null; }


    public String serialize() {
        if (blendData != null) {
            return "BLEND:" + blendData.serialize()
                    + ":" + chargesLeft + ":" + maxCharges
                    + ":" + durability + ":" + maxDurability;
        }
        String type = loadedType != null ? loadedType.name() : "EMPTY";
        return type + ":" + chargesLeft + ":" + maxCharges
                + ":" + durability + ":" + maxDurability;
    }

    public static PipeData deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        String[] parts = data.split(":");

        try {
            if (parts[0].equals("BLEND") && parts.length >= 8) {
                CigaretteType a = CigaretteType.valueOf(parts[1]);
                CigaretteType b = CigaretteType.valueOf(parts[2]);
                double ratio = Double.parseDouble(parts[3]);
                int charges = Integer.parseInt(parts[4]);
                int maxCharges = Integer.parseInt(parts[5]);
                int dur = Integer.parseInt(parts[6]);
                int maxDur = Integer.parseInt(parts[7]);

                PipeData pd = new PipeData(dur, maxDur);
                pd.loadBlend(new BlendData(a, b, ratio), maxCharges);
                pd.chargesLeft = charges;
                return pd;
            }

            if (parts.length >= 5) {
                String type = parts[0];
                int charges = Integer.parseInt(parts[1]);
                int maxCharges = Integer.parseInt(parts[2]);
                int dur = Integer.parseInt(parts[3]);
                int maxDur = Integer.parseInt(parts[4]);

                PipeData pd = new PipeData(dur, maxDur);
                if (!type.equals("EMPTY") && charges > 0) {
                    pd.load(CigaretteType.valueOf(type), maxCharges);
                    pd.chargesLeft = charges;
                }
                return pd;
            }
        } catch (Exception ignored) {}

        return new PipeData(DEFAULT_MAX_DURABILITY, DEFAULT_MAX_DURABILITY);
    }
}
