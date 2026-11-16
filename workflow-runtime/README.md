# Workflow Runtime

This module contains the database-independent part of an invoice workflow:

- serialisable operation and workflow definitions;
- the canonical invoice and reviewed field mapping logic;
- HTTP execution and outcome interpretation.

It has no Spring, JPA, or database dependency. Both the local backend and the
AWS Lambda adapter execute the same runtime code.
