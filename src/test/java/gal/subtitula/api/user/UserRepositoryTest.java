package gal.subtitula.api.user;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest extends AbstractIntegrationTest {
    @Autowired UserRepository users;

    @Test
    void savesAndFindsByEmail() {
        users.save(User.create("Ada@Example.com", "Ada", "hash"));
        assertTrue(users.findByEmail("ada@example.com").isPresent());
        assertTrue(users.existsByEmail("ada@example.com"));
    }

    @Test
    void findsByEmailCaseInsensitive() {
        users.saveAndFlush(User.create("bob@example.com", "Bob", "hash"));
        assertTrue(users.findByEmail("BOB@EXAMPLE.COM").isPresent());
        assertTrue(users.existsByEmail("Bob@Example.Com"));
    }

    @Test
    void rejectsDuplicateEmail() {
        users.saveAndFlush(User.create("dup@example.com", "One", "h1"));
        assertThrows(DataIntegrityViolationException.class,
            () -> users.saveAndFlush(User.create("dup@example.com", "Two", "h2")));
    }
}
