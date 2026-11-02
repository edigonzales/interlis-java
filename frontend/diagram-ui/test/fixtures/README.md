# Renderer contract fixture

`interactive.ili` was compiled successfully using the shared Java-engine TeaVM
JavaScript build from Core `3b60e6c1309a4897ad6d0e99f6d8dfd137520ca9`.
`interactive.json` is its actual DiagramDocument1.0, including the original opaque
snapshot/symbol identities. It is fixed renderer input, not a mocked compiler.

The live stdio/VS Code tests separately compile current source through Java.
This small browser test checks native Sprotty interactions over the neutral DTO.
Original generation command and rejected missing-OID fixture are preserved in
`interlis-vscode-evidence/phases/V2/prepare-ui-fixture*.{cjs,log}` outside the repo.
