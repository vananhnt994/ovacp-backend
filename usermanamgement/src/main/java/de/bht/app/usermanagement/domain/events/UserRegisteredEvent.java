package de.bht.app.usermanagement.domain.events;

/**
 * Die UserRegisteredEvent-Klasse repräsentiert ein Ereignis, das ausgelöst wird,
 * wenn ein Bürger erfolgreich registriert wird.
 *
 * Diese Klasse enthält Informationen über die Registrierungsdetails des Bürgers,
 * einschließlich der ID und E-Mail-Adresse.
 */
public class UserRegisteredEvent {

    private final Long id;
    private final String email;

    /**
     * Konstruktor für das UserRegisteredEvent.
     *
     * @param id Die eindeutige ID des Bürgers.
     * @param email Die E-Mail-Adresse des Bürgers.
     * @throws IllegalArgumentException Wenn ID oder Email null oder leer sind.
     */
    public UserRegisteredEvent(Long id, String email) {
        if (id == null || email == null || email.isEmpty()) {
            throw new IllegalArgumentException("ID und Email dürfen nicht null oder leer sein.");
        }
        this.id = id;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}