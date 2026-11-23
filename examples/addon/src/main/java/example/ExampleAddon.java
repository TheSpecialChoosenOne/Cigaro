package example;

import com.cigaro.api.CigaroAPI;
import com.cigaro.api.CigaroTobaccoEvent;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.TobaccoType;
import com.cigaro.data.BlendData;
import com.cigaro.data.CigaretteQuality;
import com.cigaro.Cigaro;
import org.bukkit.DyeColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;

public final class ExampleAddon extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        TobaccoType type = new TobaccoType("EXAMPLE_FOREST", "Forest", 7000, 1,
                new String[]{"FOREST"}, 0.02, 0);
        CigaroAPI.registerTobacco(type, CigaretteType.HERBAL,
                Set.of(CigaretteType.HERBAL), Set.of(CigaretteType.SHADOW));
        var items = Cigaro.getInstance().getItemManager();
        var blend = new BlendData(CigaretteType.HERBAL, CigaretteType.COFFEE, 0.6, type, TobaccoType.BURLEY);
        var cigarette = items.createBlendedCigarette(blend, CigaretteQuality.STANDARD, 1, 3, 3);
        var pack = items.createCigarettePack(java.util.List.of(cigarette), DyeColor.RED);
        var restored = items.getPackCigarettes(pack);
        if (restored.size() != 1 || items.getBlendData(restored.getFirst()).getPrimaryTobacco() != type)
            throw new IllegalStateException("Addon blend was not preserved in a cigarette pack");
        getServer().getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void onTobacco(CigaroTobaccoEvent event) {
        getLogger().info("Cigaro action: " + event.getAction());
    }
}
