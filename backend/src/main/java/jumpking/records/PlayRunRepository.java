package jumpking.records;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayRunRepository extends JpaRepository<PlayRun, String> {
    boolean existsByUserIdAndKind(long userId, String kind);
}
