package jumpking.records;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> lockById(@Param("id") String id);
    List<Match> findByEndedAtIsNullOrderById();
}
