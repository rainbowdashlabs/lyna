package de.chojo.lyna.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Links {
    private String tos = "";
    private String invite = "https://discord.com/oauth2/authorize?client_id=1065674230362017813&scope=bot&permissions=2415921152";
    private String support = "";
    private String website = "https://rainbowdashlabs.github.io/lyna/";
    private String faq = "https://rainbowdashlabs.github.io/lyna/faq";

    public String tos() {
        return tos;
    }

    public String invite() {
        return invite;
    }

    public String support() {
        return support;
    }

    public String website() {
        return website;
    }

    public String faq() {
        return faq;
    }
}
