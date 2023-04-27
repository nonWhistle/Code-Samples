package entitys;

import lombok.Getter;
import lombok.Setter;
import uk.co.dhl.smas.backend.AbstractEntity;
import uk.co.dhl.smas.backend.authority.Authority;
import uk.co.dhl.smas.backend.team.Team;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, unique = true) @Getter @Setter
    private String username;

    @Getter @Setter
    private String emailAddress;

    @NotNull @Getter @Setter
    private String password;

    @Getter @Setter
    private String mobilePhoneNumber;


    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean emailAlerts;

    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean smsAlerts;

    @Getter @Setter
    private String resetToken;

    @Column(columnDefinition = "timestamp with time zone") @Getter @Setter
    private ZonedDateTime dateResetTokenCreated;

    @NotNull
    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean admin;

    @NotNull
    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean engineer;

    /**
     * Users can run more than machine but only carry out one indirect activity at once. Therefore
     * this must be locked at a user level rather than a machine/order.
     */
    @NotNull
    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean indirectInProgress;

    /**
     * A user definable start to the alarms, alarms that are registered before this time won't be sent.
     */
    @Column(columnDefinition = "time  default '08:00'") @Getter @Setter
    private LocalTime alarmsStart;

    /**
     * A user definable stop to the alarms, alarms that are registered after this time won't be sent.
     */
    @Column(columnDefinition = "time default '18:00'") @Getter @Setter
    private LocalTime alarmsStop;

    /**
     * Called numbers but in reality controls access to the Dashboards
     * e.g. {@link uk.co.dhl.smas.ui.view.dashboard.supervisor.SupervisorView}
     * and {@link uk.co.dhl.smas.ui.view.dashboard.numbers.NumbersView}
     * <p>
     * TODO: Rename this to "dashboards" or similar.
     */
    @NotNull
    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean numbers;

    /**
     * The epicorID used for the user to interact with the EPICOR system, only used for Beverston at the moment.
     */
    @Column(columnDefinition = "integer default -1") @Getter @Setter
    private Integer epicorID;

    @OneToOne @JoinColumn(name = "team_id") @Getter @Setter
    private Team team;

    @Column(columnDefinition = "boolean default false") @Getter @Setter
    private boolean deleted = false;

    /**
     * Numbers used to be the only controlled dashboard. It was decided to move SupervisorView under this permission
     * but the database has not yet been updated hence the permission is still called numbers.
     *
     * @return True if the User is permitted to view the Dashboards.
     */
    public boolean isDashboards() { return numbers; }

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "user_authority",
            joinColumns = {@JoinColumn(name = "user_id")},
            inverseJoinColumns = {@JoinColumn(name = "authority_id")}) @Getter @Setter

    private final Set<Authority> authorities = new HashSet<>();

    public User() {
    }

    @Override
    public String toString() {
        return "User{" +
                "id = " + id + "\n" +
                "username = '" + username + '\'' + "\n" +
                "emailAddress = '" + emailAddress + '\'' + "\n" +
                "password = '" + password + '\'' + "\n" +
                "mobilePhoneNumber = '" + mobilePhoneNumber + '\'' + "\n" +
                "emailAlerts = " + emailAlerts + "\n" +
                "smsAlerts = " + smsAlerts + "\n" +
                "resetToken = '" + resetToken + '\'' + "\n" +
                "dateResetTokenCreated = " + dateResetTokenCreated + "\n" +
                "admin = " + admin + "\n" +
                "alarmsStart = " + alarmsStart + "\n" +
                "alarmsStop = " + alarmsStop + "\n" +
                "numbers = " + numbers + "\n" +
                "epicorID = " + epicorID + "\n" +
                "authorities = " + authorities + "\n" +
                '}';
    }

    public boolean isUserAvailableForAlerts() {
        LocalTime time = getLocalTimeNow();
        if (alarmsStart != null && alarmsStop != null && emailAddress != null) {
            return time.isAfter(alarmsStart) && time.isBefore(alarmsStop);
        }
        return false;
    }

    /**
     * Extracted for Testing!!
     *
     * @return The local time now.
     */
    protected LocalTime getLocalTimeNow() {
        return ZonedDateTime.now().toLocalTime();
    }

    @Override
    public Long getId() { return id; }

    /**
     * The reset token is only valid for a number of minutes to prevent use if it discovered later.
     *
     * @param numberOfMinutes The number of minutes that the token is valid for.
     * @return True if the reset token is valid.
     */
    public boolean isResetTokenValid(long numberOfMinutes) {
        return getDateResetTokenCreated().isAfter(ZonedDateTime.now().minusMinutes(numberOfMinutes));
    }
}