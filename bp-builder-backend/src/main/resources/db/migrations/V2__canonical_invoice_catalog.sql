-- Canonical invoice model based on UBL 2.1. Country/profile business rules
-- are evaluated by the selected external validation operation. Sources:
-- https://docs.oasis-open.org/ubl/os-UBL-2.1/UBL-2.1.html
-- https://docs.peppol.eu/poac/aunz/pint-aunz/bis/

INSERT INTO canonical_models (name, version, standard, schema_json, is_active)
VALUES (
    'BP Builder Canonical Invoice',
    '1.0',
    'OASIS UBL 2.1 Invoice',
    '{"documentType":"Invoice","ublVersion":"2.1"}'::jsonb,
    TRUE
)
ON CONFLICT (name, version) DO UPDATE
SET standard = EXCLUDED.standard,
    schema_json = EXCLUDED.schema_json,
    is_active = TRUE,
    updated_at = NOW();

WITH model AS (
    SELECT id FROM canonical_models
    WHERE name = 'BP Builder Canonical Invoice' AND version = '1.0'
), fields(field_path, data_type, cardinality, ubl_xpath, xsd_required, constraints, description) AS (
    VALUES
    ('document.customization_id','string','0..1','/ubl:Invoice/cbc:CustomizationID',FALSE,'{}'::jsonb,'Specification identifier supplied by an operation or workflow input.'),
    ('document.profile_id','string','0..1','/ubl:Invoice/cbc:ProfileID',FALSE,'{}'::jsonb,'Business process identifier supplied by an operation or workflow input.'),
    ('invoice.id','identifier','1..1','/ubl:Invoice/cbc:ID',TRUE,'{}'::jsonb,'Invoice identifier.'),
    ('invoice.issue_date','date','1..1','/ubl:Invoice/cbc:IssueDate',TRUE,'{"format":"YYYY-MM-DD"}'::jsonb,'Invoice issue date.'),
    ('invoice.due_date','date','0..1','/ubl:Invoice/cbc:DueDate',FALSE,'{"format":"YYYY-MM-DD"}'::jsonb,'Payment due date.'),
    ('invoice.type_code','code','0..1','/ubl:Invoice/cbc:InvoiceTypeCode',FALSE,'{}'::jsonb,'Invoice document type code.'),
    ('invoice.currency_code','code','0..1','/ubl:Invoice/cbc:DocumentCurrencyCode',FALSE,'{"codeList":"ISO 4217"}'::jsonb,'Invoice currency.'),
    ('invoice.buyer_reference','string','0..1','/ubl:Invoice/cbc:BuyerReference',FALSE,'{}'::jsonb,'Buyer reference when no purchase order reference is used.'),
    ('seller.endpoint_id','identifier','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cbc:EndpointID',FALSE,'{}'::jsonb,'Seller electronic address.'),
    ('seller.name','string','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName',FALSE,'{}'::jsonb,'Seller legal or trading name.'),
    ('seller.tax_id','identifier','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme/cbc:CompanyID',FALSE,'{}'::jsonb,'Seller ABN or NZ GST identifier.'),
    ('seller.address.street','string','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PostalAddress/cbc:StreetName',FALSE,'{}'::jsonb,'Seller street address.'),
    ('seller.address.city','string','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PostalAddress/cbc:CityName',FALSE,'{}'::jsonb,'Seller city or locality.'),
    ('seller.address.state','string','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PostalAddress/cbc:CountrySubentity',FALSE,'{}'::jsonb,'Seller state or region.'),
    ('seller.address.postal_code','string','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PostalAddress/cbc:PostalZone',FALSE,'{}'::jsonb,'Seller postcode.'),
    ('seller.address.country_code','code','0..1','/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PostalAddress/cac:Country/cbc:IdentificationCode',FALSE,'{"codeList":"ISO 3166-1 alpha-2"}'::jsonb,'Seller country code.'),
    ('buyer.endpoint_id','identifier','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cbc:EndpointID',FALSE,'{}'::jsonb,'Buyer electronic address.'),
    ('buyer.name','string','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName',FALSE,'{}'::jsonb,'Buyer legal or trading name.'),
    ('buyer.tax_id','identifier','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyTaxScheme/cbc:CompanyID',FALSE,'{}'::jsonb,'Buyer ABN or NZ GST identifier.'),
    ('buyer.address.street','string','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PostalAddress/cbc:StreetName',FALSE,'{}'::jsonb,'Buyer street address.'),
    ('buyer.address.city','string','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PostalAddress/cbc:CityName',FALSE,'{}'::jsonb,'Buyer city or locality.'),
    ('buyer.address.state','string','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PostalAddress/cbc:CountrySubentity',FALSE,'{}'::jsonb,'Buyer state or region.'),
    ('buyer.address.postal_code','string','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PostalAddress/cbc:PostalZone',FALSE,'{}'::jsonb,'Buyer postcode.'),
    ('buyer.address.country_code','code','0..1','/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PostalAddress/cac:Country/cbc:IdentificationCode',FALSE,'{"codeList":"ISO 3166-1 alpha-2"}'::jsonb,'Buyer country code.'),
    ('totals.line_extension_amount','decimal','0..1','/ubl:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount',FALSE,'{}'::jsonb,'Sum of invoice line net amounts.'),
    ('totals.tax_exclusive_amount','decimal','0..1','/ubl:Invoice/cac:LegalMonetaryTotal/cbc:TaxExclusiveAmount',FALSE,'{}'::jsonb,'Invoice total excluding tax.'),
    ('totals.tax_amount','decimal','0..1','/ubl:Invoice/cac:TaxTotal/cbc:TaxAmount',FALSE,'{}'::jsonb,'Total GST amount.'),
    ('totals.tax_inclusive_amount','decimal','0..1','/ubl:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount',FALSE,'{}'::jsonb,'Invoice total including tax.'),
    ('totals.payable_amount','decimal','1..1','/ubl:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount',TRUE,'{}'::jsonb,'Amount due for payment.'),
    ('lines[*].id','identifier','1..n','/ubl:Invoice/cac:InvoiceLine/cbc:ID',TRUE,'{}'::jsonb,'Invoice line identifier.'),
    ('lines[*].description','string','1..n','/ubl:Invoice/cac:InvoiceLine/cac:Item/cbc:Description',FALSE,'{}'::jsonb,'Item name or description.'),
    ('lines[*].quantity','decimal','1..n','/ubl:Invoice/cac:InvoiceLine/cbc:InvoicedQuantity',FALSE,'{}'::jsonb,'Invoiced quantity.'),
    ('lines[*].unit_code','code','0..n','/ubl:Invoice/cac:InvoiceLine/cbc:InvoicedQuantity/@unitCode',FALSE,'{}'::jsonb,'Quantity unit code.'),
    ('lines[*].net_amount','decimal','1..n','/ubl:Invoice/cac:InvoiceLine/cbc:LineExtensionAmount',TRUE,'{}'::jsonb,'Invoice line net amount.'),
    ('lines[*].unit_price','decimal','1..n','/ubl:Invoice/cac:InvoiceLine/cac:Price/cbc:PriceAmount',FALSE,'{}'::jsonb,'Item net unit price.'),
    ('lines[*].tax_category_code','code','0..n','/ubl:Invoice/cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory/cbc:ID',FALSE,'{"allowedValues":["S","E","Z","G","O"]}'::jsonb,'A-NZ GST category code.'),
    ('lines[*].tax_rate','decimal','0..n','/ubl:Invoice/cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory/cbc:Percent',FALSE,'{}'::jsonb,'GST percentage for the line.'),
    ('lines[*].tax_scheme_code','code','0..n','/ubl:Invoice/cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory/cac:TaxScheme/cbc:ID',FALSE,'{"allowedValues":["GST"]}'::jsonb,'A-NZ tax scheme code.')
)
INSERT INTO canonical_fields
    (canonical_model_id, field_path, data_type, cardinality, ubl_xpath, xsd_required, constraints, description)
SELECT model.id, fields.field_path, fields.data_type, fields.cardinality, fields.ubl_xpath,
       fields.xsd_required, fields.constraints, fields.description
FROM model CROSS JOIN fields
ON CONFLICT (canonical_model_id, field_path) DO UPDATE
SET data_type = EXCLUDED.data_type,
    cardinality = EXCLUDED.cardinality,
    ubl_xpath = EXCLUDED.ubl_xpath,
    xsd_required = EXCLUDED.xsd_required,
    constraints = EXCLUDED.constraints,
    description = EXCLUDED.description;
