# Legal and privacy release checklist

- [ ] Complete legal owner name, year, governing jurisdiction and contact in LICENSE.
- [ ] Obtain legal review and confirm the owner has authority to license the app.
- [ ] Complete privacy controller/contact, effective date, and audience/children disclosures.
- [ ] Verify the final merged manifest and all runtime permissions.
- [ ] Verify ML Kit and all SDK data flows against current vendor terms.
- [ ] Generate a complete dependency SBOM and licence report from the exact release build.
- [ ] Bundle required third-party licence texts and notices.
- [ ] Confirm in-app access to privacy and third-party notices.
- [ ] Verify deletion, backup, logging, and external-app handoff behaviour on device.
- [ ] Recheck all documentation whenever dependencies or app behaviour changes.

**Release gate:** Do not represent licensing or privacy compliance as complete while
any applicable item remains unchecked.
