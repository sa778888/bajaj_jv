# webhook-solver-vscode

## What this does
- On startup it POSTs to the Generate Webhook endpoint with candidate details.
- Receives `webhook` and `accessToken`.
- Prints Drive link (odd/even regNo) for you to open the PDF and craft SQL.
- Reads `finalQuery` from (priority): `application.properties` -> `finalQuery.txt` -> console input.
- Saves the query to a local H2 DB (`./data/solutions`) and POSTs it to the testWebhook endpoint with `Authorization: Bearer <accessToken>` header.

## Build
```bash
mvn -U clean package
