package jumpking.records;

import java.util.function.Function;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RunOwners {
    public record Owner(long id, String type, Long mergedInto) {}
    private static class OwnerChanged extends RuntimeException {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public RunOwners(JdbcTemplate jdbc, TransactionTemplate transactions) { this.jdbc = jdbc; this.transactions = transactions; }
    public Owner read(long id) {
        return jdbc.query("SELECT id,type,merged_into_user_id FROM users WHERE id=?", (row, index) -> new Owner(row.getLong("id"), row.getString("type"),
            row.getObject("merged_into_user_id", Long.class)), id).stream().findFirst().orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Identity not found"));
    }
    public void lockPair(long first, long second) {
        lock(Math.min(first, second)); if (first != second) lock(Math.max(first, second));
    }
    private void lock(long id) {
        if (jdbc.query("SELECT id FROM users WHERE id=? FOR UPDATE", (row, index) -> row.getLong(1), id).isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Identity not found");
        }
    }
    public <T> T write(long source, Function<Long, T> writer) {
        return writeAll(java.util.List.of(source), owners -> writer.apply(owners.get(source)));
    }
    public <T> T writeAll(java.util.Collection<Long> sources, Function<java.util.Map<Long, Long>, T> writer) {
        var unique = new java.util.TreeSet<>(sources);
        for (int attempt = 0; attempt <= unique.size(); attempt++) {
            var targets = new java.util.LinkedHashMap<Long, Long>(); var locks = new java.util.TreeSet<>(unique);
            for (long source : unique) { var owner = read(source); long target = owner.mergedInto == null ? source : owner.mergedInto; targets.put(source, target); locks.add(target); }
            try {
                return transactions.execute(status -> {
                    locks.forEach(this::lock);
                    for (long source : unique) {
                        var owner = read(source); long current = owner.mergedInto == null ? source : owner.mergedInto;
                        // Pointer đổi trước khi lấy lock: rollback rồi khóa lại toàn bộ theo thứ tự ID.
                        if (current != targets.get(source)) throw new OwnerChanged();
                        if (current != source && !"ACCOUNT".equals(read(current).type)) throw new IllegalStateException("Invalid merge target");
                    }
                    return writer.apply(java.util.Map.copyOf(targets));
                });
            } catch (OwnerChanged retry) { /* Pointer chỉ được thiết lập một lần. */ }
        }
        throw new ApiException(409, "CONFLICT", "History ownership changed; retry the write");
    }
}
