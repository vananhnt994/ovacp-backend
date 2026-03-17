package de.bht.app.usermanagement.application.services;

import de.bht.app.usermanagement.domain.repository.UserRepository;
import de.bht.app.usermanagement.application.dto.UserDto;
import de.bht.app.usermanagement.domain.events.UserLoggedInEvent;
import de.bht.app.usermanagement.domain.model.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Der CitizenService verwaltet die Geschäftslogik im Zusammenhang mit Bürgern.
 * Er bietet Funktionen zum Erstellen, Suchen und Anmelden von Bürgern sowie zur Validierung von Anmeldedaten.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Reguläre Ausdrücke zur Validierung von E-Mail-Adressen und Passwörtern
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    private static final String PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";

    /**
     * Konstruktor für den CitizenService.
     *
     * @param userRepository Das Repository zur Verwaltung von Bürgerdaten.
     * @param eventPublisher Der Publisher für Anwendungsereignisse.
     */
    @Autowired
    public UserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sucht einen Bürger anhand seiner E-Mail-Adresse.
     *
     * @param email Die E-Mail-Adresse des Bürgers.
     * @return Das gefundene User-Objekt oder null, wenn kein Bürger gefunden wurde.
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Gibt eine Liste aller registrierten Bürger zurück.
     *
     * @return Eine Liste von User-Objekten.
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Erstellt einen neuen Bürger basierend auf den übergebenen Daten.
     *
     * @param userDto Die Daten des zu erstellenden Bürgers.
     * @throws Exception Wenn die E-Mail bereits registriert ist oder ein anderer Fehler auftritt.
     */
    public void createUser(UserDto userDto) throws Exception {
        User user = new User();
        user.setEmail(userDto.getEmail());
        user.setPassword(userDto.getPassword());

        if (userRepository != null && findByEmail(user.getEmail()) != null) {
            throw new Exception("E-Mail bereits registriert");
        }

        System.out.println("Bürger registriert: " + user.getEmail());

        assert userRepository != null;
        userRepository.save(user);
    }

    /**
     * Meldet einen Bürger an, indem seine Anmeldedaten überprüft werden.
     *
     * @param userDto Die Anmeldedaten des Nutzers.
     * @throws IllegalArgumentException Wenn die Anmeldedaten ungültig sind.
     */
    public void login(UserDto userDto) {
        User user = userRepository.findByEmail(userDto.getEmail());

        if (user != null && validateCredentials(userDto.getEmail(), userDto.getPassword())) {
            // Beispielmethode zur Validierung
            UserLoggedInEvent event = new UserLoggedInEvent(user.getEmail(), user.getPassword());
            eventPublisher.publishEvent(event);
        } else {
            throw new IllegalArgumentException("Invalid credentials");
        }
    }

    /**
     * Validiert die Anmeldedaten eines Bürgers (E-Mail und Passwort).
     *
     * @param email Die E-Mail-Adresse des Bürgers.
     * @param password Das Passwort des Bürgers.
     * @return true, wenn die Anmeldedaten gültig sind; andernfalls false.
     */
    public boolean validateCredentials(String email, String password) {
        return Pattern.matches(EMAIL_REGEX, email) && Pattern.matches(PASSWORD_REGEX, password);
    }
}