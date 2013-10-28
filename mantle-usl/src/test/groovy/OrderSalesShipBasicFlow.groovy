/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

/* To run these make sure moqui, and mantle are in place and run:
    "gradle cleanAll load runtime/mantle/mantle-usl:test"
   Or to quick run with saved DB copy use "gradle loadSave" once then each time "gradle reloadSave runtime/mantle/mantle-usl:test"
 */
class OrderSalesShipBasicFlow extends Specification {
    @Shared
    protected final static Logger logger = LoggerFactory.getLogger(OrderSalesShipBasicFlow.class)
    @Shared
    ExecutionContext ec
    @Shared
    String cartOrderId = null, orderPartSeqId
    @Shared
    Map setInfoOut, shipResult

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        // set an effective date so data check works, etc; Long value (when set from Locale of john.doe, US/Central): 1383411600000
        ec.user.setEffectiveTime(ec.l10n.parseTimestamp("2013-11-02 12:00:00.0", null))

        ec.entity.tempSetSequencedIdPrimary("mantle.account.method.PaymentGatewayResponse", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.ledger.transaction.AcctgTrans", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.shipment.ShipmentItemSource", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.product.asset.Asset", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.product.asset.AssetReservation", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.product.issuance.AssetIssuance", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.account.invoice.Invoice", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.account.payment.PaymentApplication", 55500, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.order.OrderItemBilling", 55500, 10)
    }

    def cleanupSpec() {
        ec.entity.tempResetSequencedIdPrimary("mantle.account.method.PaymentGatewayResponse")
        ec.entity.tempResetSequencedIdPrimary("mantle.ledger.transaction.AcctgTrans")
        ec.entity.tempResetSequencedIdPrimary("mantle.shipment.ShipmentItemSource")
        ec.entity.tempResetSequencedIdPrimary("mantle.product.asset.Asset")
        ec.entity.tempResetSequencedIdPrimary("mantle.product.asset.AssetReservation")
        ec.entity.tempResetSequencedIdPrimary("mantle.product.issuance.AssetIssuance")
        ec.entity.tempResetSequencedIdPrimary("mantle.account.invoice.Invoice")
        ec.entity.tempResetSequencedIdPrimary("mantle.account.payment.PaymentApplication")
        ec.entity.tempResetSequencedIdPrimary("mantle.order.OrderItemBilling")
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    def "create Sales Order"() {
        when:
        ec.user.loginUser("joe@public.com", "moqui", null)

        String productStoreId = "POPC_DEFAULT"
        EntityValue productStore = ec.entity.makeFind("mantle.product.store.ProductStore").condition("productStoreId", productStoreId).one()
        String currencyUomId = productStore.defaultCurrencyUomId
        String priceUomId = productStore.defaultCurrencyUomId
        // String defaultLocale = productStore.defaultLocale
        // String organizationPartyId = productStore.organizationPartyId
        String vendorPartyId = productStore.organizationPartyId
        String customerPartyId = ec.user.userAccount.partyId

        Map priceMap = ec.service.sync().name("mantle.product.ProductServices.get#ProductPrice")
                .parameters([productId:'DEMO_1_1', priceUomId:priceUomId, productStoreId:productStoreId,
                vendorPartyId:vendorPartyId, customerPartyId:customerPartyId]).call()

        Map addOut1 = ec.service.sync().name("mantle.order.OrderServices.add#OrderProductQuantity")
                .parameters([orderId:cartOrderId, productId:'DEMO_1_1', quantity:1, customerPartyId:customerPartyId,
                    currencyUomId:currencyUomId, productStoreId:productStoreId]).call()

        cartOrderId = addOut1.orderId
        orderPartSeqId = addOut1.orderPartSeqId

        Map addOut2 = ec.service.sync().name("mantle.order.OrderServices.add#OrderProductQuantity")
                .parameters([orderId:cartOrderId, productId:'DEMO_3_1', quantity:5, customerPartyId:customerPartyId,
                    currencyUomId:currencyUomId, productStoreId:productStoreId]).call()
        Map addOut3 = ec.service.sync().name("mantle.order.OrderServices.add#OrderProductQuantity")
                .parameters([orderId:cartOrderId, productId:'DEMO_2_1', quantity:7, customerPartyId:customerPartyId,
                currencyUomId:currencyUomId, productStoreId:productStoreId]).call()

        setInfoOut = ec.service.sync().name("mantle.order.OrderServices.set#OrderBillingShippingInfo")
                .parameters([orderId:cartOrderId, paymentMethodId:'CustJqpCc', shippingPostalContactMechId:'CustJqpAddr',
                    shippingTelecomContactMechId:'CustJqpTeln', shipmentMethodEnumId:'ShMthNoShipping']).call()
        ec.service.sync().name("mantle.order.OrderServices.place#Order").parameters([orderId:cartOrderId]).call()

        ec.user.logoutUser()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.order.OrderHeader orderId="${cartOrderId}" entryDate="1383418800000" placedDate="1383418800000"
                statusId="OrderApproved" currencyUomId="USD" productStoreId="POPC_DEFAULT" grandTotal="140.68"/>

            <mantle.account.payment.Payment paymentId="${setInfoOut.paymentId}" paymentMethodId="CustJqpCc"
                paymentMethodTypeEnumId="PmtCreditCard" orderId="${cartOrderId}" orderPartSeqId="01"
                statusId="PmntAuthorized" amount="140.68" amountUomId="USD"/>
            <mantle.account.method.PaymentGatewayResponse paymentGatewayResponseId="55500"
                paymentGatewayConfigId="TEST_APPROVE" paymentOperationEnumId="PgoAuthorize"
                paymentId="${setInfoOut.paymentId}" paymentMethodId="CustJqpCc" amount="140.68" amountUomId="USD"
                referenceNum="TEST" transactionDate="1383418800000" resultSuccess="Y" resultDeclined="N" resultNsf="N"
                resultBadExpire="N" resultBadCardNumber="N"/>

            <mantle.order.OrderPart orderId="${cartOrderId}" orderPartSeqId="01" vendorPartyId="ORG_BIZI_RETAIL"
                customerPartyId="CustJqp" shipmentMethodEnumId="ShMthNoShipping" postalContactMechId="CustJqpAddr"
                telecomContactMechId="CustJqpTeln" partTotal="140.68"/>
            <mantle.order.OrderItem orderId="${cartOrderId}" orderItemSeqId="01" orderPartSeqId="01" itemTypeEnumId="ItemProduct"
                productId="DEMO_1_1" itemDescription="Demo Product One-One" quantity="1" unitAmount="16.99"
                unitListPrice="16.99" isModifiedPrice="N"/>
            <mantle.order.OrderItem orderId="${cartOrderId}" orderItemSeqId="02" orderPartSeqId="01" itemTypeEnumId="ItemProduct"
                productId="DEMO_3_1" itemDescription="Demo Product Three-One" quantity="5" unitAmount="7.77"
                unitListPrice="7.77" isModifiedPrice="N"/>
            <mantle.order.OrderItem orderId="${cartOrderId}" orderItemSeqId="03" orderPartSeqId="01" itemTypeEnumId="ItemProduct"
                productId="DEMO_2_1" itemDescription="Demo Product Two-One" quantity="7" unitAmount="12.12"
                unitListPrice="12.12" isModifiedPrice="N"/>
        </entity-facade-xml>""").check()
        logger.info("create Sales Order data check results: " + dataCheckErrors)

        then:
        priceMap.price == 16.99
        priceMap.priceUomId == 'USD'
        vendorPartyId == 'ORG_BIZI_RETAIL'
        customerPartyId == 'CustJqp'

        dataCheckErrors.size() == 0
    }

    def "validate Asset Reservation"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- Asset created, issued, changed record in detail -->

            <mantle.product.asset.Asset assetId="DEMO_1_1A" assetTypeEnumId="AstTpInventory" statusId="AstAvailable"
                ownerPartyId="ORG_BIZI_RETAIL" productId="DEMO_1_1" hasQuantity="Y"
                quantityOnHandTotal="100" availableToPromiseTotal="99" dateReceived="1265184000000"
                facilityId="ORG_BIZI_RETAIL_WH"/>
            <mantle.product.asset.AssetReservation assetReservationId="55500" assetId="DEMO_1_1A" productId="DEMO_1_1"
                orderId="${cartOrderId}" orderItemSeqId="01" reservationOrderEnumId="AsResOrdFifoRec" quantity="1"
                reservedDate="1383418800000" sequenceNum="1"/>
            <mantle.product.asset.AssetDetail assetId="DEMO_1_1A" assetDetailSeqId="01" effectiveDate="1265184000000"
                quantityOnHandDiff="100" availableToPromiseDiff="100" productId="DEMO_1_1"/>
            <mantle.product.asset.AssetDetail assetId="DEMO_1_1A" assetDetailSeqId="02" effectiveDate="1383418800000"
                availableToPromiseDiff="-1" assetReservationId="55500" productId="DEMO_1_1"/>

            <mantle.product.asset.Asset assetId="DEMO_3_1A" assetTypeEnumId="AstTpInventory" statusId="AstAvailable"
                ownerPartyId="ORG_BIZI_RETAIL" productId="DEMO_3_1" hasQuantity="Y" quantityOnHandTotal="5"
                availableToPromiseTotal="0" dateReceived="1265184000000" facilityId="ORG_BIZI_RETAIL_WH"/>
            <mantle.product.asset.AssetReservation assetReservationId="55501" assetId="DEMO_3_1A" productId="DEMO_3_1"
                orderId="${cartOrderId}" orderItemSeqId="02" reservationOrderEnumId="AsResOrdFifoRec" quantity="5"
                reservedDate="1383418800000" sequenceNum="1"/>
            <mantle.product.asset.AssetDetail assetId="DEMO_3_1A" assetDetailSeqId="01" effectiveDate="1265184000000"
                quantityOnHandDiff="5" availableToPromiseDiff="5" productId="DEMO_3_1"/>
            <mantle.product.asset.AssetDetail assetId="DEMO_3_1A" assetDetailSeqId="02" effectiveDate="1383418800000"
                availableToPromiseDiff="-5" assetReservationId="55501" productId="DEMO_3_1"/>

            <!-- this is an auto-created Asset based on the inventory issuance -->
            <mantle.product.asset.Asset assetId="55500" assetTypeEnumId="AstTpInventory" statusId="AstAvailable"
                ownerPartyId="ORG_BIZI_RETAIL" productId="DEMO_2_1" hasQuantity="Y" quantityOnHandTotal="0"
                availableToPromiseTotal="-7" dateReceived="1383418800000" facilityId="ORG_BIZI_RETAIL_WH"/>
            <mantle.product.asset.AssetReservation assetReservationId="55502" assetId="55500" productId="DEMO_2_1"
                orderId="${cartOrderId}" orderItemSeqId="03" reservationOrderEnumId="AsResOrdFifoRec"
                quantity="7" quantityNotAvailable="7" reservedDate="1383418800000"/>
            <mantle.product.asset.AssetDetail assetId="55500" assetDetailSeqId="01" effectiveDate="1383418800000"
                availableToPromiseDiff="-7" assetReservationId="55502" productId="DEMO_2_1"/>
        </entity-facade-xml>""").check()
        logger.info("validate Asset Reservation data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "ship Sales Order"() {
        when:
        ec.user.loginUser("john.doe", "moqui", null)

        shipResult = ec.service.sync().name("mantle.shipment.ShipmentServices.ship#OrderPart")
                .parameters([orderId:cartOrderId, orderPartSeqId:orderPartSeqId]).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- Shipment created -->
            <mantle.shipment.Shipment shipmentId="${shipResult.shipmentId}" shipmentTypeEnumId="ShpTpSales"
                statusId="ShipShipped" fromPartyId="ORG_BIZI_RETAIL" toPartyId="CustJqp"/>
            <mantle.shipment.ShipmentPackage shipmentId="${shipResult.shipmentId}" shipmentPackageSeqId="01"/>

            <mantle.shipment.ShipmentItem shipmentId="${shipResult.shipmentId}" productId="DEMO_1_1" quantity="1"/>
            <mantle.shipment.ShipmentItemSource shipmentItemSourceId="55500" shipmentId="${shipResult.shipmentId}"
                productId="DEMO_1_1" orderId="${cartOrderId}" orderItemSeqId="01" statusId="SisPacked" quantity="1"
                invoiceId="55500" invoiceItemSeqId="01"/>
            <mantle.shipment.ShipmentPackageContent shipmentId="${shipResult.shipmentId}" shipmentPackageSeqId="01"
                productId="DEMO_1_1" quantity="1"/>

            <mantle.shipment.ShipmentItem shipmentId="${shipResult.shipmentId}" productId="DEMO_3_1" quantity="5"/>
            <mantle.shipment.ShipmentItemSource shipmentItemSourceId="55501" shipmentId="${shipResult.shipmentId}"
                productId="DEMO_3_1" orderId="${cartOrderId}" orderItemSeqId="02" statusId="SisPacked" quantity="5"
                invoiceId="55500" invoiceItemSeqId="02"/>
            <mantle.shipment.ShipmentPackageContent shipmentId="${shipResult.shipmentId}" shipmentPackageSeqId="01"
                productId="DEMO_3_1" quantity="5"/>

            <mantle.shipment.ShipmentItem shipmentId="${shipResult.shipmentId}" productId="DEMO_2_1" quantity="7"/>
            <mantle.shipment.ShipmentItemSource shipmentItemSourceId="55502" shipmentId="${shipResult.shipmentId}"
                productId="DEMO_2_1" orderId="${cartOrderId}" orderItemSeqId="03" statusId="SisPacked" quantity="7"
                invoiceId="55500" invoiceItemSeqId="03"/>
            <mantle.shipment.ShipmentPackageContent shipmentId="${shipResult.shipmentId}" shipmentPackageSeqId="01"
                productId="DEMO_2_1" quantity="7"/>

            <mantle.shipment.ShipmentRouteSegment shipmentId="${shipResult.shipmentId}" shipmentRouteSegmentSeqId="01"
                destPostalContactMechId="CustJqpAddr" destTelecomContactMechId="CustJqpTeln"/>
            <mantle.shipment.ShipmentPackageRouteSeg shipmentId="${shipResult.shipmentId}" shipmentPackageSeqId="01"
                shipmentRouteSegmentSeqId="01"/>
        </entity-facade-xml>""").check()
        logger.info("ship Sales Order data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Sales Order Complete"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- OrderHeader status to Completed -->
            <mantle.order.OrderHeader orderId="${cartOrderId}" statusId="OrderCompleted"/>
        </entity-facade-xml>""").check()
        logger.info("validate Sales Order Complete data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Asset Issuance"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- Asset created, issued, changed record in detail -->

            <mantle.product.asset.Asset assetId="DEMO_1_1A" quantityOnHandTotal="99" availableToPromiseTotal="99"/>
            <mantle.product.issuance.AssetIssuance assetIssuanceId="55500" assetId="DEMO_1_1A" assetReservationId="55500"
                orderId="${cartOrderId}" orderItemSeqId="01" shipmentId="${shipResult.shipmentId}" productId="DEMO_1_1"
                quantity="1"/>
            <mantle.product.asset.AssetDetail assetId="DEMO_1_1A" assetDetailSeqId="03" effectiveDate="1383418800000"
                quantityOnHandDiff="-1" assetReservationId="55500" shipmentId="${shipResult.shipmentId}"
                productId="DEMO_1_1" assetIssuanceId="55500"/>

            <mantle.product.asset.Asset assetId="DEMO_3_1A" quantityOnHandTotal="0" availableToPromiseTotal="0"/>
            <mantle.product.issuance.AssetIssuance assetIssuanceId="55501" assetId="DEMO_3_1A" assetReservationId="55501"
                orderId="${cartOrderId}" orderItemSeqId="02" shipmentId="${shipResult.shipmentId}" productId="DEMO_3_1"
                quantity="5"/>
            <mantle.product.asset.AssetDetail assetId="DEMO_3_1A" assetDetailSeqId="03" effectiveDate="1383418800000"
                quantityOnHandDiff="-5" assetReservationId="55501" shipmentId="${shipResult.shipmentId}"
                productId="DEMO_3_1" assetIssuanceId="55501"/>

            <!-- this is an auto-created Asset based on the inventory issuance -->
            <mantle.product.asset.Asset assetId="55500" quantityOnHandTotal="-7" availableToPromiseTotal="-7"/>
            <mantle.product.issuance.AssetIssuance assetIssuanceId="55502" assetId="55500" assetReservationId="55502"
                orderId="${cartOrderId}" orderItemSeqId="03" shipmentId="${shipResult.shipmentId}" productId="DEMO_2_1"
                quantity="7"/>
            <mantle.product.asset.AssetDetail assetId="55500" assetDetailSeqId="02" effectiveDate="1383418800000"
                quantityOnHandDiff="-7" assetReservationId="55502" shipmentId="${shipResult.shipmentId}"
                productId="DEMO_2_1" assetIssuanceId="55502"/>
        </entity-facade-xml>""").check()
        logger.info("validate Asset Issuance data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Shipment Invoice"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- Invoice created and Finalized (status set by action in SECA rule) -->
            <mantle.account.invoice.Invoice invoiceId="55500" invoiceTypeEnumId="InvoiceSales"
                fromPartyId="ORG_BIZI_RETAIL" toPartyId="CustJqp" statusId="InvoicePmtRecvd" invoiceDate="1383418800000"
                description="Invoice for Order ${cartOrderId} part 01 and Shipment ${shipResult.shipmentId}" currencyUomId="USD"/>

            <mantle.account.invoice.InvoiceItem invoiceId="55500" invoiceItemSeqId="01" itemTypeEnumId="ItemProduct"
                productId="DEMO_1_1" quantity="1" amount="16.99" description="Demo Product One-One" itemDate="1383418800000"/>
            <mantle.order.OrderItemBilling orderItemBillingId="55500" orderId="${cartOrderId}" orderItemSeqId="01"
                invoiceId="55500" invoiceItemSeqId="01" assetIssuanceId="55500" shipmentId="${shipResult.shipmentId}"
                quantity="1" amount="16.99"/>

            <mantle.account.invoice.InvoiceItem invoiceId="55500" invoiceItemSeqId="02" itemTypeEnumId="ItemProduct"
                productId="DEMO_3_1" quantity="5" amount="7.77" description="Demo Product Three-One" itemDate="1383418800000"/>
            <mantle.order.OrderItemBilling orderItemBillingId="55501" orderId="${cartOrderId}" orderItemSeqId="02"
                invoiceId="55500" invoiceItemSeqId="02" assetIssuanceId="55501" shipmentId="${shipResult.shipmentId}"
                quantity="5" amount="7.77"/>

            <mantle.account.invoice.InvoiceItem invoiceId="55500" invoiceItemSeqId="03" itemTypeEnumId="ItemProduct"
                productId="DEMO_2_1" quantity="7" amount="12.12" description="Demo Product Two-One" itemDate="1383418800000"/>
            <mantle.order.OrderItemBilling orderItemBillingId="55502" orderId="${cartOrderId}" orderItemSeqId="03"
                invoiceId="55500" invoiceItemSeqId="03" assetIssuanceId="55502" shipmentId="${shipResult.shipmentId}"
                quantity="7" amount="12.12"/>

            <mantle.account.payment.PaymentApplication paymentApplicationId="55500" paymentId="${setInfoOut.paymentId}"
                invoiceId="55500" amountApplied="140.68" appliedDate="1383418800000"/>
        </entity-facade-xml>""").check()
        logger.info("validate Shipment Invoice data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "validate Shipment Invoice Accounting Transaction"() {
        when:
        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- AcctgTrans created for Finalized Invoice -->
            <mantle.ledger.transaction.AcctgTrans acctgTransId="55500" acctgTransTypeEnumId="AttSalesInvoice"
                organizationPartyId="ORG_BIZI_RETAIL" transactionDate="1383418800000" isPosted="Y"
                postedDate="1383418800000" glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="CustJqp"
                invoiceId="55500"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55500" acctgTransEntrySeqId="01" debitCreditFlag="C"
                amount="16.99" glAccountId="401000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"
                productId="DEMO_1_1" invoiceItemSeqId="01"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55500" acctgTransEntrySeqId="02" debitCreditFlag="C"
                amount="38.85" glAccountId="401000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"
                productId="DEMO_3_1" invoiceItemSeqId="02"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55500" acctgTransEntrySeqId="03" debitCreditFlag="C"
                amount="84.84" glAccountId="401000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"
                productId="DEMO_2_1" invoiceItemSeqId="03"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="55500" acctgTransEntrySeqId="04" debitCreditFlag="D"
                amount="140.68" glAccountTypeEnumId="ACCOUNTS_RECEIVABLE" glAccountId="120000"
                reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
        </entity-facade-xml>""").check()
        logger.info("validate Shipment Invoice Accounting Transaction data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }
}
