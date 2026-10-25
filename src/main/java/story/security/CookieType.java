package story.security;

public enum CookieType {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token");

    private final String name;

    CookieType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}