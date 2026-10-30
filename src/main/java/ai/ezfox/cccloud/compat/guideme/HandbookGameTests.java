package ai.ezfox.cccloud.compat.guideme;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import guideme.libs.mdast.MdAst;
import guideme.libs.mdast.MdastOptions;
import guideme.libs.mdast.YamlFrontmatterExtension;
import guideme.libs.mdast.gfm.GfmTableMdastExtension;
import guideme.libs.mdast.gfmstrikethrough.GfmStrikethroughMdastExtension;
import guideme.libs.mdast.mdx.MdxMdastExtension;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstLink;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdx.MdxSyntax;
import guideme.libs.micromark.extensions.YamlFrontmatterSyntax;
import guideme.libs.micromark.extensions.gfm.GfmTableSyntax;
import guideme.libs.micromark.extensions.gfmstrikethrough.GfmStrikethroughSyntax;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * The generated handbook (tools/gen_handbook.ts) parses with GuideMe's own Markdown/MDX parser in both languages:
 * every page has a navigation title, only known tags are used, recipe tags name existing items (the bridges only
 * when AE2 / RS are present) or, for {@code <Recipe>}, one of our recipe files, and every page link points to an
 * existing page.
 */
public final class HandbookGameTests {
    /** The docs pages (cloud/web/src/docs/content/index.ts PAGES); "start" is the index. */
    static final List<String> PAGES = List.of("index", "cc-basics", "dashboard", "redstone", "lua", "ui-library", "network", "upgrades", "automation",
        "recipes", "mods", "bridges", "accounts", "api", "security", "faq");
    static final List<String> LANGS = List.of("", "_de_de/");
    /** RecipesFor / RecipeFor name an item, Recipe one of our recipe ids. */
    private static final Set<String> TAGS = Set.of("RecipeFor", "RecipesFor", "Recipe");
    private static final String ROOT = "/assets/cccloud/guides/cccloud/handbook/";

    private HandbookGameTests() {
    }

    public static void init() {
        Integrations.test("guideme_handbook", () -> HandbookGameTests::pages);
    }

    private static MdastOptions options() {
        // Same extensions as GuideMe's PageCompiler.
        return new MdastOptions()
            .withSyntaxExtension(MdxSyntax.INSTANCE)
            .withSyntaxExtension(YamlFrontmatterSyntax.INSTANCE)
            .withSyntaxExtension(GfmTableSyntax.INSTANCE)
            .withSyntaxExtension(GfmStrikethroughSyntax.INSTANCE)
            .withMdastExtension(MdxMdastExtension.INSTANCE)
            .withMdastExtension(YamlFrontmatterExtension.INSTANCE)
            .withMdastExtension(GfmTableMdastExtension.INSTANCE)
            .withMdastExtension(GfmStrikethroughMdastExtension.INSTANCE);
    }

    static @Nullable String read(String path) {
        try (InputStream in = CCCloud.class.getResourceAsStream(ROOT + path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void pages(GameTestHelper helper) {
        var problems = new ArrayList<String>();
        for (var lang : LANGS) {
            for (var page : PAGES) {
                String path = lang + page + ".md";
                String source = read(path);
                if (source == null) {
                    problems.add(path + ": missing");
                    continue;
                }
                try {
                    var root = MdAst.fromMarkdown(source, options());
                    check(path, lang, root, problems);
                } catch (RuntimeException e) {
                    problems.add(path + ": " + e.getMessage());
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "handbook problems: " + problems);
        helper.succeed();
    }

    private static void check(String path, String lang, MdAstNode root, List<String> problems) {
        boolean[] title = {false};
        walk(root, node -> {
            if (node instanceof MdAstYamlFrontmatter fm && fm.value.contains("title:")) title[0] = true;
            if (node instanceof MdxJsxElementFields el) {
                if (!TAGS.contains(el.name())) problems.add(path + ": unknown tag " + el.name());
                String id = el.getAttributeString("id", "");
                var item = Identifier.tryParse(id);
                if (el.name().equals("Recipe")) {
                    if (item == null || !item.getNamespace().equals(CCCloud.MODID) || CCCloud.class.getResource("/data/cccloud/recipe/" + item.getPath() + ".json") == null) {
                        problems.add(path + ": unknown recipe " + id);
                    }
                } else {
                    boolean optional = id.endsWith("me_cloud_bridge") && !ModList.get().isLoaded("ae2")
                        || id.endsWith("rs_cloud_bridge") && !ModList.get().isLoaded("refinedstorage");
                    if (!optional && (item == null || !BuiltInRegistries.ITEM.containsKey(item))) problems.add(path + ": unknown item " + id);
                }
                if (el.getAttributeString("fallbackText", "").isEmpty()) problems.add(path + ": recipe without fallback text");
            }
            if (node instanceof MdAstLink link && !link.url.startsWith("http")) {
                String target = link.url.replaceAll("#.*$", "");
                if (!target.isEmpty() && read(lang + target) == null) problems.add(path + ": broken link " + link.url);
            }
        });
        if (!title[0]) problems.add(path + ": no navigation title");
    }

    private static void walk(MdAstNode node, java.util.function.Consumer<MdAstNode> visitor) {
        visitor.accept(node);
        if (node instanceof MdAstParent<?> parent) for (var child : parent.children()) walk((MdAstNode) child, visitor);
    }
}
