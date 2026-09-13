# Issue 60 Docker Release TODO

- [x] Core and Web images build from the checked-out repository with one commit identity.
- [x] A new MySQL volume initializes all migrations and development fixtures.
- [x] An existing volume restarts without duplicate fixtures or Flyway failure.
- [x] Web-grade and Core-grade host ports are configurable.
- [x] Core and Web expose the requested commit identity.
- [x] Chrome Playwright passes all six Issue 60 acceptance criteria.
- [x] Java, Node, planning, independent review, and pull-request CI gates pass.
