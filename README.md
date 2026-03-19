# ClearingHouse_v1_0 - Corrected Sequence Diagrams

## Pipeline Execution Order (for reference)

The main pipeline has this flow structure:

```
Request path (top-down):
  1. InitializationAndLogging_request  →  Initialization Stage
  2. ServiceOperations_request         →  InputMessageTransformations Stage
  3. Syniverse Route (route-node)      →  Only routes to MQ if isNNP='false'

Response path (bottom-up):
  3. Syniverse Route response transform
  2. ServiceOperations_response        →  ServiceResponse Stage (empty body)
  1. InitializationAndLogging_response →  Logging Stage (publish to MessageLogger)

Error path (on any fault):
  → ServiceErrorHandler               →  ErrorLookup + log + reply fault
```

---

## 1. Happy Path: submitPortInRequest (isNNP = false → Syniverse MQ)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant CH as ClearingHouse_v1_0<br/>(Proxy + Pipeline)
    participant MQ as SyniverseMQ<br/>(Business Service)
    participant Log as MessageLogger_v1_0

    Client->>CH: submitPortInRequest()

    rect rgb(255, 248, 220)
        Note over CH: 1. Initialization Stage (request)
        CH->>CH: Create trace variable
        CH->>CH: Extract serviceName from SOAPAction
        CH->>CH: Create responseHeader (UUID msgID)
        CH->>CH: Capture serviceRequestHeader & serviceRequestBody
        CH->>CH: Set vendorName = 'OSB'
        CH->>CH: Validate MessageHeader
    end

    rect rgb(230, 240, 255)
        Note over CH: 2. InputMessageTransformations Stage (request)
        CH->>CH: Validate submitPortInRequest body against XSD
        CH->>CH: Extract ONSP, NNSP from request
        CH->>CH: Transform message via submitPortInRequest.xqy
        CH->>CH: Extract CTN (PORTED_NUM)
        Note over CH: NNP Detection
        CH->>CH: Set isNNP = 'false' (default)
        CH->>CH: Check ONSP/NNSP prefix for 'NNP'
        CH->>CH: If no prefix match → query LERG DB (eligibilitySqlQuery)
        CH->>CH: Result: isNNP = 'false'
    end

    rect rgb(220, 255, 220)
        Note over CH: 3. Syniverse Route (route-node)
        Note over CH: Condition: isNNP = 'false' → route to MQ
        CH->>CH: Set vendorName = 'SyniverseMQ'
        CH->>CH: Create serviceData structure
        CH->>CH: Set MQ headers (msgID, Format='MQSTR   ')
        CH->>MQ: Route to SyniverseMQ.bix
        MQ-->>CH: MQ Response
        CH->>CH: Update serviceData (responseTimestamp, status=SUCCESS)
    end

    rect rgb(230, 240, 255)
        Note over CH: 4. ServiceResponse Stage (response)
        CH->>CH: Create empty response body<br/>(fire-and-forget pattern)
    end

    rect rgb(255, 248, 220)
        Note over CH: 5. Logging Stage (response)
        CH->>CH: Restore responseHeader
        CH->>CH: Set activityStatus = 'SUCCESS'
        CH->>CH: Set msgType = 'RESPONSE'
        CH->>CH: Set responseTimestamp
        CH->>CH: Build log_Request (trace, request, response, serviceData)
        CH--)Log: Publish log_Request (async, one-way)
    end

    CH-->>Client: SOAP Response (empty body, SUCCESS header)
```

---

## 2. Happy Path: submitPortInRequest (isNNP = true → NNP Port-In)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant CH as ClearingHouse_v1_0<br/>(Proxy + Pipeline)
    participant NNPIn as NNPPortInProcessor<br/>(Pipeline Service)
    participant Intq as portInOrderInteliQuentAPI<br/>(Business Service)
    participant DB as NNP_DBAdapter<br/>(Business Service)
    participant NP as NetworkProvider_v1_0<br/>(Proxy Service)
    participant Email as NNPEmailService<br/>(Business Service)
    participant Log as MessageLogger_v1_0

    Client->>CH: submitPortInRequest()

    rect rgb(255, 248, 220)
        Note over CH: 1. Initialization Stage (request)
        CH->>CH: Create trace, serviceName, responseHeader
        CH->>CH: Capture request headers & body
        CH->>CH: Set vendorName = 'OSB'
        CH->>CH: Validate MessageHeader
    end

    rect rgb(230, 240, 255)
        Note over CH: 2. InputMessageTransformations Stage (request)
        CH->>CH: Validate submitPortInRequest body
        CH->>CH: Extract ONSP, NNSP, CTN
        CH->>CH: Transform message via submitPortInRequest.xqy
        Note over CH: NNP Detection
        CH->>CH: ONSP starts with 'NNP' OR DB query NNP_IND = 'Y'
        CH->>CH: Set isNNP = 'true'
        CH->>CH: Set portInRelated = 'true'
    end

    rect rgb(240, 230, 255)
        Note over CH,NNPIn: NNP Port-In Callout (within ServiceOperations request)
        CH->>NNPIn: Call NNPPortInProcessor pipeline
        Note over NNPIn: Initialization
        NNPIn->>NNPIn: Set serviceName = 'NNPPortInProcessor'
        NNPIn->>NNPIn: Create responseHeader, capture body
        NNPIn->>NNPIn: Detect operation (portInOrder = true)
        Note over NNPIn: DetermineNNPPortInOperations
        NNPIn->>NNPIn: Transform via inteliquentPortInOrderRequest.xqy
        NNPIn->>Intq: Route to portInOrderInteliQuentAPI.bix
        Intq-->>NNPIn: Inteliquent API Response
        Note over NNPIn: NNPPortInProcessorResponse
        NNPIn->>NNPIn: Process API response
        Note over NNPIn: InsertDatabase
        NNPIn->>DB: Insert record via NNP_DBAdapter.bix
        DB-->>NNPIn: DB Response
        Note over NNPIn: InvokeNetworkProvider_v1_0
        NNPIn->>NP: Call NetworkProvider_v1_0
        NP-->>NNPIn: Response
        Note over NNPIn: Logging
        NNPIn--)Log: Publish log (async)
        NNPIn-->>CH: Return
    end

    rect rgb(220, 255, 220)
        Note over CH: 3. Syniverse Route (route-node)
        Note over CH: Condition: isNNP = 'true' → SKIP MQ route (no-op)
    end

    rect rgb(230, 240, 255)
        Note over CH: 4. ServiceResponse Stage (response)
        CH->>CH: Create empty response body
    end

    rect rgb(255, 248, 220)
        Note over CH: 5. Logging Stage (response)
        CH->>CH: Restore responseHeader, set SUCCESS
        CH--)Log: Publish log_Request (async)
    end

    CH-->>Client: SOAP Response (empty body, SUCCESS header)
```

---

## 3. Happy Path: Port-Out Operations (isNNP = true → NNP Port-Out)

Applies to: `processDelayForPortOut`, `processConfirmForPortOut`, `processResolutionRequiredForPortOut`

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant CH as ClearingHouse_v1_0<br/>(Proxy + Pipeline)
    participant NNPOut as NNPPortOutProcessor<br/>(Pipeline Service)
    participant Intq as InteliQuentAPI<br/>(Business Service)
    participant DB as NNP_DBAdapter<br/>(Business Service)
    participant ACM as AtomicCustomerMgmt_v1_0
    participant Email as NNPEmailService<br/>(Business Service)
    participant Log as MessageLogger_v1_0

    Client->>CH: processDelayForPortOut() / processConfirmForPortOut()

    rect rgb(255, 248, 220)
        Note over CH: 1. Initialization Stage
        CH->>CH: Create trace, serviceName, responseHeader
    end

    rect rgb(230, 240, 255)
        Note over CH: 2. InputMessageTransformations Stage
        CH->>CH: Validate, transform, extract ONSP/NNSP/CTN
        CH->>CH: isNNP = 'true', portOutRelated = 'true'
    end

    rect rgb(255, 230, 240)
        Note over CH,NNPOut: NNP Port-Out Callout (within ServiceOperations request)
        CH->>NNPOut: Call NNPPortOutProcessor pipeline

        Note over NNPOut: Initialization
        NNPOut->>NNPOut: Set up trace, variables

        Note over NNPOut: DetermineOperationAndTiming
        NNPOut->>NNPOut: Determine operation type & timing

        NNPOut->>Intq: Route to InteliQuentAPI.bix
        Intq-->>NNPOut: API Response

        Note over NNPOut: API Response Handler
        NNPOut->>NNPOut: Parse Inteliquent response

        Note over NNPOut: API Response Transformation
        NNPOut->>NNPOut: Transform response

        Note over NNPOut: Update Database
        NNPOut->>DB: Insert/update via NNP_DBAdapter.bix
        DB-->>NNPOut: DB Response

        Note over NNPOut: MemoOnEmail
        NNPOut->>ACM: Add memo via AtomicCustomerManagement_v1_0
        ACM-->>NNPOut: Response

        Note over NNPOut: Email Response Transformation
        alt replyChannel = 'EMAIL'
            NNPOut->>NNPOut: Build rejection email (nnpRejectionEmail.xqy)
            NNPOut->>Email: Send email via NNPEmailService.bix
        end

        Note over NNPOut: Logging
        NNPOut--)Log: Publish log (async)
        NNPOut-->>CH: Return
    end

    rect rgb(220, 255, 220)
        Note over CH: 3. Syniverse Route → SKIP (isNNP = true)
    end

    rect rgb(230, 240, 255)
        Note over CH: 4. ServiceResponse → empty body
    end

    rect rgb(255, 248, 220)
        Note over CH: 5. Logging Stage
        CH--)Log: Publish log_Request (async)
    end

    CH-->>Client: SOAP Response (SUCCESS)
```

---

## 4. Error Path (any stage failure)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant CH as ClearingHouse_v1_0<br/>(Proxy + Pipeline)
    participant ErrLkp as ErrorLookup_v1_0<br/>(Proxy Service)
    participant Log as MessageLogger_v1_0

    Client->>CH: submitPortInRequest()

    Note over CH: Processing stages 1-3...
    Note over CH: ERROR occurs (validation, transform,<br/>MQ route, NNP callout, etc.)

    rect rgb(255, 220, 220)
        Note over CH: ServiceErrorHandler (error pipeline)
        CH->>CH: Restore responseHeader
        CH->>CH: Check error type:<br/>ServiceException? SOAP Fault? OSB Fault?

        alt faultCode exists (set by operation-level handler)
            CH->>CH: lookupCode = faultCode
        else No faultCode
            CH->>CH: Derive lookupCode via<br/>ServiceLevelFaultCodes.xqy
        end

        alt faultDetails exists
            CH->>CH: Use existing faultDetails
        else osbFault exists
            CH->>CH: faultDetails = osbFault
        else SOAP Fault in body
            CH->>CH: faultDetails = body/soap-env:Fault
        else
            CH->>CH: faultDetails = $fault
        end

        CH->>ErrLkp: wsCallout: lookup(vendorName, lookupCode)
        ErrLkp-->>CH: lookup_Response (errorCode, errorDescription)

        CH->>CH: Build WnpPortingException<br/>(errorCode, errorDescription, faultDetails, msgID)
        CH->>CH: Replace body with SOAP Fault
        CH->>CH: Set responseTimestamp

        CH--)Log: Publish log_Request with fault data (async)
    end

    CH-->>Client: SOAP Fault Response<br/>(WnpPortingException)
```

---

## 5. Combined Overview (all paths in one diagram)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant CH as ClearingHouse_v1_0
    participant MQ as SyniverseMQ
    participant NNPIn as NNPPortInProcessor
    participant NNPOut as NNPPortOutProcessor
    participant Intq as Inteliquent APIs
    participant DB as NNP_DBAdapter
    participant NP as NetworkProvider_v1_0
    participant ACM as AtomicCustomerMgmt
    participant Email as NNPEmailService
    participant Log as MessageLogger_v1_0
    participant Err as ErrorLookup_v1_0

    C->>CH: SOAP Request (any of 9 operations)

    Note over CH: Initialization Stage<br/>(trace, serviceName, responseHeader,<br/>capture request, validate header)

    Note over CH: InputMessageTransformations Stage<br/>(validate, transform, extract ONSP/NNSP,<br/>NNP detection)

    alt isNNP = false (Standard Syniverse Path)
        Note over CH: Syniverse Route
        CH->>CH: Set vendorName='SyniverseMQ', build MQ headers
        CH->>MQ: Route to SyniverseMQ.bix
        MQ-->>CH: MQ Response
        CH->>CH: serviceData status = SUCCESS

    else isNNP = true, Port-In (submitPortInRequest, processACT, processSUP1/3, processMPS)
        CH->>NNPIn: Call NNPPortInProcessor
        NNPIn->>Intq: Call Inteliquent API (portInOrder / activate / update / cancel)
        Intq-->>NNPIn: API Response
        NNPIn->>DB: Insert result
        DB-->>NNPIn: OK
        NNPIn->>NP: Call NetworkProvider_v1_0
        NP-->>NNPIn: OK
        NNPIn--)Log: Log (async)
        NNPIn-->>CH: Return

    else isNNP = true, Port-Out (processDelay, processConfirm, processResolutionRequired)
        CH->>NNPOut: Call NNPPortOutProcessor
        NNPOut->>Intq: Call Inteliquent API (reject / disconnect)
        Intq-->>NNPOut: API Response
        NNPOut->>DB: Update NNP DB
        DB-->>NNPOut: OK
        NNPOut->>ACM: Add memo
        ACM-->>NNPOut: OK
        opt replyChannel = EMAIL
            NNPOut->>Email: Send rejection email
        end
        NNPOut--)Log: Log (async)
        NNPOut-->>CH: Return
    end

    Note over CH: ServiceResponse Stage<br/>(create empty response body)

    Note over CH: Logging Stage
    CH--)Log: Publish log_Request (async)

    CH-->>C: SOAP Response (SUCCESS, empty body)

    rect rgb(255, 220, 220)
        Note over CH: ERROR PATH (on any failure)
        CH->>CH: Resolve faultCode & faultDetails
        CH->>Err: wsCallout: lookup(vendorName, lookupCode)
        Err-->>CH: errorCode + errorDescription
        CH->>CH: Build WnpPortingException
        CH--)Log: Publish error log (async)
        CH-->>C: SOAP Fault (WnpPortingException)
    end
```

---

## Key Differences from Original Diagram

| # | Issue in Original | Correction |
|---|---|---|
| 1 | Initialization stage missing | Added as first stage before any routing |
| 2 | InputMessageTransformations stage missing | Added - validates, transforms, detects NNP |
| 3 | ServiceErrorHandler shown in happy path | Moved to error-only branch |
| 4 | ErrorLookup called in happy path | Only called during error handling |
| 5 | Syniverse MQ + NNP shown sequentially | They are mutually exclusive (isNNP flag) |
| 6 | NNP_DBAdapter calling NNP_DBAdapter-concrete | DBAdapter is a single JDBC business service |
| 7 | Missing Inteliquent API calls | Added for both port-in and port-out |
| 8 | Missing NetworkProvider_v1_0 | Added in NNPPortInProcessor flow |
| 9 | Missing AtomicCustomerManagement | Added in NNPPortOutProcessor flow |
| 10 | Logging shown as synchronous | Corrected to async publish (one-way) |
