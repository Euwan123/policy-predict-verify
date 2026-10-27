# Authentication Policy Simulator

A Java desktop app for comparing simulated authentication-policy outcomes with sandbox replays of historical login data.

## Overview

This project lets you:
- load historical login data from CSV
- compare predictions against a replayed sandbox model
- test policy settings for lockout, MFA, and session timeout
- evaluate which policy type is most trustworthy to simulate

The interface uses FlatLaf for consistent native-feeling controls, a restrained graphite palette, and a single blue accent.

## Project files

- `Main.java` — desktop GUI and policy logic
- `historical_login_data.csv` — sample dataset used for evaluation
- `pom.xml` — Java 17 build and FlatLaf dependency

## How it works

The app models three policy types:
- Lockout Threshold
- MFA Requirement
- Session Timeout

For each policy, it:
1. predicts how many users and attackers would be affected
2. replays the data with small realistic noise
3. compares the predicted result to the actual replayed outcome
4. scores the trustworthiness of the simulation

## Run it locally

Install Java 17 and Maven, then run from the project folder:

```powershell
mvn package
java -jar target\policy-predict-verify.jar
```

## Notes

This is a demonstration application for policy analysis and visual comparison, not a production authentication system.
