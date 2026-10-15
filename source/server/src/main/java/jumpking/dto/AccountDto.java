package jumpking.dto;

public final class AccountDto {
    private AccountDto() {}
    public record Account(String id, String username, String email) {}
    public record Me(boolean authenticated, boolean guest, Account account) {}
    public record LoginResponse(boolean authenticated, boolean guest, Account account, boolean guestHistoryAvailable) {}
}
