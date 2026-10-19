# Dashboard screenshots

PNG captures of the working dashboard using authenticated local test accounts.
The filename identifies the actual application permission role and page.

- [Administrator — Overview](<Administrator - Overview.png>)
- [Administrator — Charging stations](<Administrator - Charging Stations.png>)
- [Administrator — Assets](<Administrator - Assets.png>)
- [Administrator — People and roles](<Administrator - People and Roles.png>)
- [Administrator — Mobile overview](<Administrator - Mobile Overview.png>)
- [Operator — Charging stations](<Operator - Charging Stations.png>)
- [Viewer — Sessions](<Viewer - Sessions.png>)
- [Driver — My sessions](<Driver - My Sessions.png>)
- [Anonymous — Sign in](<Anonymous - Sign In.png>)

These captures use synthetic H2 data. The local test transport runs Spring
Security, Spring MVC and JPA through MockMvc, with no replacement frontend API
fixtures. OCPP transactions are created through the application's protocol
service. The browser check creates an asset using the real dashboard form.

CPO and eMSP are roaming market roles, separate from Administrator, Operator,
Viewer and Driver permissions. These images do not claim completed CPO/eMSP
roaming workflow coverage.