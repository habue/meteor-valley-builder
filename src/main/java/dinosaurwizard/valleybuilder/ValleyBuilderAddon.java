package dinosaurwizard.valleybuilder;

import dinosaurwizard.valleybuilder.modules.ValleyBuilder;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ValleyBuilderAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Valley Builder");

        // Modules
        Modules.get().add(new ValleyBuilder());
    }

    @Override
    public void onRegisterCategories() {
        // Custom category not defined
    }

    @Override
    public String getPackage() {
        return "dinosaurwizard.valleybuilder";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Gingerbeard5773", "meteor-valley-builder");
    }
}
