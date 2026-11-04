import dev.angularlite.ComponentNavigation;

public class ComponentNavigationCheck {
    public static void main(String[] args) {
        String html = "@if (condition) {\n <custom-child [label]=\"value\"></custom-child>\n}";
        for (String tag : new String[]{"<custom-child", "</custom-child"}) {
            int start = html.indexOf(tag) + (tag.startsWith("</") ? 2 : 1);
            for (int offset = start; offset < start + "custom-child".length(); offset++)
                if (!ComponentNavigation.isTagName(html, offset)) throw new AssertionError("Tag position " + offset);
        }
        for (String other : new String[]{"condition", "label", "value"})
            if (ComponentNavigation.isTagName(html, html.indexOf(other))) throw new AssertionError(other);
        String expressions = "@if (country; as selected) { {{ selected.name }} {{ country.name }} {{ unknown }} }";
        for (String identifier : new String[]{"country", "selected", "name", "unknown"})
            if (!ComponentNavigation.isNavigationPosition(expressions, expressions.indexOf(identifier)))
                throw new AssertionError("Must query Angular for " + identifier);
        if (ComponentNavigation.isNavigationPosition(expressions, expressions.indexOf('{')))
            throw new AssertionError("Do not navigate braces");
        String binding = "@if (condition) { <custom-child [label]='plainLabel' /> }";
        int start = binding.indexOf("plainLabel"), end = start + "plainLabel".length();
        if (ComponentNavigation.identifierOffset(binding, end) != end - 1)
            throw new AssertionError("Caret before closing quote must navigate the previous identifier");
        System.out.println("PASS component navigation: opening/closing tag inside @if; attributes and expressions excluded");
    }
}
