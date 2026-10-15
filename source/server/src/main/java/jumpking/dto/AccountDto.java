package jumpking.dto;

public final class AccountDto {
    private AccountDto() {}
    public record Account(String id, String username, String email, boolean crownUnlocked) {}
    public record Me(boolean authenticated, boolean guest, Account account, boolean crownUnlocked) {}
    public record LoginResponse(boolean authenticated, boolean guest, Account account, boolean guestHistoryAvailable) {}
}
