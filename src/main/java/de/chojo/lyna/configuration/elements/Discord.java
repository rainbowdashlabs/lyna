package de.chojo.lyna.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Discord {
    private OAuth oauth = new OAuth();

    public OAuth oauth() {
        return oauth;
    }

    public static class OAuth {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";

        public String clientId() {
            return clientId;
        }

        public String clientSecret() {
            return clientSecret;
        }

        public String redirectUri() {
            return redirectUri;
        }
    }
}
