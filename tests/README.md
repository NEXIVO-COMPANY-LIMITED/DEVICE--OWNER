# Testing

This directory contains all test files and test procedures for the Device Owner Firmware Security implementation.

## Structure

- **unit/**: Unit tests
  - `firmware/`: Firmware component unit tests
  - `native/`: Native library unit tests
  - `app/`: Application unit tests

- **integration/**: Integration tests
  - `firmware_integration/`: Firmware integration tests
  - `end_to_end/`: End-to-end system tests

- **manual/**: Manual test procedures
  - Button blocking test procedures
  - Fastboot blocking tests
  - Factory reset prevention tests

- **scripts/**: Test automation scripts
  - `run_tests.sh`: Run all tests
  - `test_firmware.sh`: Test firmware components

## Running Tests

### Unit Tests
```bash
./gradlew test
```

### Integration Tests
```bash
./tools/tests/scripts/run_tests.sh
```

### Manual Tests
Follow procedures in `/tests/manual/` directory.

## Test Coverage

See individual test directories for coverage reports and test documentation.
