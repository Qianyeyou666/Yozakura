package gq.yozakura.club;

public final class ClubAuthResult {
    private final String token;
    private final ClubUser user;

    public ClubAuthResult(String token, ClubUser user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public ClubUser getUser() {
        return user;
    }
}
