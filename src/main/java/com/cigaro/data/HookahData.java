package com.cigaro.data;

public class HookahData {

    private CigaretteType flavorType;
    private BlendData blendData;

    private boolean hasWater;
    private int chargesLeft;
    private int maxCharges;

    public static final int MAX_CHARGES = 20;

    public HookahData() {
        this.hasWater = false;
        this.chargesLeft = 0;
        this.maxCharges = MAX_CHARGES;
    }

    public boolean isReady() {
        return hasWater && chargesLeft > 0
                && (flavorType != null || blendData != null);
    }

    public void fill(CigaretteType type) {
        this.flavorType = type;
        this.blendData = null;
        this.chargesLeft = MAX_CHARGES;
    }

    public void fillBlend(BlendData blend) {
        this.flavorType = null;
        this.blendData = blend;
        this.chargesLeft = MAX_CHARGES;
    }

    public void addWater() {
        this.hasWater = true;
    }

    public void puff() {
        if (chargesLeft > 0) chargesLeft--;
        if (chargesLeft <= 0) {
            flavorType = null;
            blendData = null;
            hasWater = false;
        }
    }

    public boolean isBlend() { return blendData != null; }

    public CigaretteType getFlavorType() { return flavorType; }
    public void setFlavorType(CigaretteType type) { this.flavorType = type; }

    public BlendData getBlendData() { return blendData; }
    public void setBlendData(BlendData data) { this.blendData = data; }

    public boolean hasWater() { return hasWater; }
    public void setHasWater(boolean v) { this.hasWater = v; }

    public int getChargesLeft() { return chargesLeft; }
    public void setChargesLeft(int v) { this.chargesLeft = v; }

    public int getMaxCharges() { return maxCharges; }


    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(hasWater ? "1" : "0").append(":");
        sb.append(chargesLeft).append(":");

        if (blendData != null) {
            sb.append("BLEND:").append(blendData.serialize());
        } else if (flavorType != null) {
            sb.append(flavorType.name());
        } else {
            sb.append("NONE");
        }

        return sb.toString();
    }

    public static HookahData deserialize(String data) {
        if (data == null || data.isEmpty()) return new HookahData();

        String[] parts = data.split(":", 4);
        HookahData hd = new HookahData();

        try {
            hd.hasWater = parts[0].equals("1");
            hd.chargesLeft = Integer.parseInt(parts[1]);

            if (parts.length >= 3) {
                String flavor = parts[2];
                if (flavor.equals("BLEND") && parts.length >= 4) {
                    hd.blendData = BlendData.deserialize(parts[3]);
                } else if (!flavor.equals("NONE")) {
                    hd.flavorType = CigaretteType.valueOf(flavor);
                }
            }
        } catch (Exception ignored) {}

        return hd;
    }
}
