package de.bht.app.usermanagement.domain.repository;

import de.bht.app.usermanagement.domain.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("Select c from User c Where c.email =:email")
    User findByEmail(String email);


}