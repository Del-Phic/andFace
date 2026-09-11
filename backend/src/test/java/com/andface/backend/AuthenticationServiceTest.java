package com.andface.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.andface.backend.dto.request.Requests.ResultRequest;
import com.andface.backend.entity.*;
import com.andface.backend.exception.ApiException;
import com.andface.backend.repository.*;
import com.andface.backend.service.AuthenticationService;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
  @Test
  void rejectsNonFiniteMetricsBeforeTouchingDatabase() {
    var logs = mock(AuthenticationLogRepository.class);
    var devices = mock(DeviceRepository.class);
    var users = mock(UserRepository.class);
    var service = new AuthenticationService(logs, devices, users);
    var r =
        new ResultRequest(
            "USER_1",
            "test",
            AuthenticationLog.Result.SUCCESS,
            Double.NaN,
            0.8,
            0.8,
            0.8,
            0.1,
            true,
            null,
            null,
            null);
    assertThrows(
        ApiException.class, () -> service.save(new UserAccount("USER_1", "test", "hash"), r));
    verifyNoInteractions(logs, devices, users);
  }
}
