package de.bht.app.usermanagement.application.controllers;

import de.bht.app.usermanagement.application.dto.UserDto;
import de.bht.app.usermanagement.application.services.UserService;
import de.bht.app.usermanagement.domain.model.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Der UserController verwaltet die Anfragen im Zusammenhang mit Nutzer.
 * Er bietet Endpunkte für die Registrierung und Anmeldung von Nutzern sowie
 * das Abrufen aller registrierten Nutzer.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // Reguläre Ausdrücke zur Validierung von E-Mail-Adressen und Passwörtern
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    private static final String PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";

    /**
     * Konstruktor für den UserController.
     *
     * @param userServiceImpl Die Implementierung des UserService, die zur Verarbeitung von Nutzerdaten verwendet wird.
     */
    public UserController(UserService userServiceImpl) {
        this.userService = userServiceImpl;
    }

    /**
     * Gibt eine Liste aller registrierten Nutzer zurück.
     *
     * @return Eine Liste von User-Objekten.
     */
    @GetMapping("/")
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    /**
     * Registriert einen neuen Nutzer.
     *
     * @param userDto Die Daten des zu registrierenden Nutzers.
     * @return Eine ResponseEntity mit dem Status der Anfrage und den entsprechenden Daten oder Fehlermeldungen.
     * @throws Exception Wenn ein Fehler bei der Registrierung auftritt.
     */
    @PostMapping(value = "/signup", consumes = {"application/json"})
    public ResponseEntity<?> registerNewUser(@RequestBody UserDto userDto) throws Exception {
        try {
            if (!validateEmail(userDto.getEmail()))
                throwIllegalArgumentException("Invalid email format");
            if (!validatePassword(userDto.getPassword()))
                throwIllegalArgumentException("Password must be at least 8 characters long and include a number, a lowercase letter, an uppercase letter, and a special character.");
            if (userService.findByEmail(userDto.getEmail()) != null)
                throw new Exception("Email already exists");

            userService.createUser(userDto);
            System.out.println("User " + userDto.getEmail() + " ist registriert");
            return ResponseEntity.ok().body(userService.findByEmail(userDto.getEmail()));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            if (e.getMessage().contains("bereits registriert")) {
                return ResponseEntity.status(400).body(e.getMessage());
            }
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * Meldet einen Nutzer an.
     *
     * @param userDto Die Anmeldedaten des Nutzers.
     * @return Eine ResponseEntity mit dem Status der Anfrage und den entsprechenden Daten oder Fehlermeldungen.
     * @throws Exception Wenn ein Fehler bei der Anmeldung auftritt.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDto userDto) throws Exception {
        try {
            if (!validateEmail(userDto.getEmail())) {
                throw new IllegalArgumentException("Invalid email format");
            }
            userService.login(userDto);
            System.out.println("Login erfolgreich!");
            return ResponseEntity.status(200).body(userService.findByEmail(userDto.getEmail()));
        } catch (Exception e) {
            System.out.println("Login fehlgeschlagen!");
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    /**
     * Validiert das Format einer E-Mail-Adresse anhand eines regulären Ausdrucks.
     *
     * @param email Die zu validierende E-Mail-Adresse.
     * @return true, wenn das Format gültig ist; andernfalls false.
     */
    public boolean validateEmail(String email) {
        return Pattern.matches(EMAIL_REGEX, email);
    }

    /**
     * Validiert das Format eines Passworts anhand eines regulären Ausdrucks.
     *
     * @param password Das zu validierende Passwort.
     * @return true, wenn das Format gültig ist; andernfalls false.
     */
    public boolean validatePassword(String password) {
        return Pattern.matches(PASSWORD_REGEX, password);
    }

    /**
     * Wirft eine IllegalArgumentException mit einer angegebenen Fehlermeldung.
     *
     * @param exceptionMessage Die Fehlermeldung für die Ausnahme.
     */
    public void throwIllegalArgumentException(String exceptionMessage) {
        throw new IllegalArgumentException(exceptionMessage);
    }
}