package entitys;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class UserTest {

    @Test
    public void isResetTokenValid() {

        User user = new User();
        user.setDateResetTokenCreated(ZonedDateTime.now());

        // Reset token has just been created, it should be valid.
        assertTrue(user.isResetTokenValid(60L));

        user.setDateResetTokenCreated(ZonedDateTime.now().minusMinutes(61L));

        // Reset token was created 61 minutes ago, will not be valid for a 60 minute window.
        assertFalse(user.isResetTokenValid(60L));

        // Reset token was created 61 minutes ago, will be valid for a 62 minute window.
        assertTrue(user.isResetTokenValid(62L));
    }

    @Test
    void isUserAvailableForAlerts() {
        //Set up
        User user = new User();
        User spy = Mockito.spy(user);
        doReturn(LocalTime.of(9,1)).when(spy).getLocalTimeNow();
        spy.setAlarmsStart(LocalTime.of(9,0));
        spy.setAlarmsStop(LocalTime.of(10,0));
        spy.setEmailAddress("testing@testing.co.uk");

        //Check when a user is available for alerts
        assertTrue(spy.isUserAvailableForAlerts());
        doReturn(LocalTime.of(9,59)).when(spy).getLocalTimeNow();
        assertTrue(spy.isUserAvailableForAlerts());

        //Check when a user is NOT available for alerts
        doReturn(LocalTime.of(10,1)).when(spy).getLocalTimeNow();
        assertFalse(spy.isUserAvailableForAlerts());
        doReturn(LocalTime.of(8,59)).when(spy).getLocalTimeNow();
        assertFalse(spy.isUserAvailableForAlerts());

        //Check when a user has not defined the alarm stop start
        //Start null
        spy.setAlarmsStart(null);
        assertFalse(spy.isUserAvailableForAlerts());
        //Stop null
        spy.setAlarmsStart(LocalTime.of(9,0));
        spy.setAlarmsStop(null);
        assertFalse(spy.isUserAvailableForAlerts());
        //Both null
        spy.setAlarmsStart(null);
        assertFalse(spy.isUserAvailableForAlerts());

        //Check when there email address is null
        spy.setEmailAddress(null);
        assertFalse(spy.isUserAvailableForAlerts());
    }
}