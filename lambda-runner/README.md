# Lambda Runner

This module adapts `workflow-runtime` to AWS Lambda. The handler is:

`com.bpbuilder.lambda_runner.WorkflowLambdaHandler::handleRequest`

Build the jar from the repository root:

```text
mvnw.cmd -pl lambda-runner -am package
```

This produces two shaded jars: `lambda-runner-0.0.1-SNAPSHOT-aws.jar` (about
8 MB, with the Step Functions client, for entry functions) and
`lambda-runner-0.0.1-SNAPSHOT-component.jar` (about 2.5 MB, without the AWS
SDK, for component functions).

## Deployed workflows

The backend deploys a saved workflow (`POST /api/workflows/{id}/deployment`)
as connected AWS components, all built from this jar:

- **Component functions** `bp-op-{operation id}`: one per catalog operation,
  shared by every workflow that uses it. The jar carries `component.json` (the
  operation's reviewed definition) and only the environment variables that
  operation references. A Step Functions task sends
  `{"sequence": n, "state": {...}}`; the function runs that one step and
  returns the next `WorkflowState`.
- **State machine** `bp-workflow-{id}` (Standard): one task per step, each
  followed by a choice that continues while the state has no status, so the
  flow ends at `Completed`, `Invoice invalid` or `Stopped`. The Step Functions
  console shows the workflow as a diagram and every run's path through it.
- **Entry function** `bp-workflow-{id}`: carries `entry.json` and has a public
  function URL. It starts the state machine with the input, waits for it to
  finish and answers with the result.

A component is updated only when its operation (code or secrets) changed;
deleting a workflow removes its entry function and state machine but keeps the
shared components.

The entry function URL accepts a JSON body in the workflow's input type, at
most 160 KB (Step Functions passes at most 256 KB between steps):

```text
POST <function URL>
{"input": "<PDF as Base64>"}          for a PDF workflow
{"input": "<UBL XML text>"}           for a UBL workflow
{"input": {"invoice": {...}, ...}}    for a canonical invoice workflow
```

It answers with the run status, message, canonical invoice, produced documents
(for example `UBL_XML`) and the validation and communication outcomes. A
missing, non-JSON or oversized input is a 400; a failed execution is a 502.
Without `component.json` or `entry.json` the handler runs a complete
`WorkflowInvocation` in one function.

## One-time AWS setup

Replace `ACCOUNT_ID` below with the AWS account ID.

1. **Function role** `bp-builder-lambda-exec`, trusted by `lambda.amazonaws.com`,
   with `AWSLambdaBasicExecutionRole` (logs) and an inline policy that lets an
   entry function run its state machine:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": ["states:StartExecution", "states:DescribeExecution"],
       "Resource": [
         "arn:aws:states:ap-southeast-2:ACCOUNT_ID:stateMachine:bp-workflow-*",
         "arn:aws:states:ap-southeast-2:ACCOUNT_ID:execution:bp-workflow-*:*"
       ]
     }]
   }
   ```
2. **State machine role** `bp-builder-sfn-exec`, trusted by
   `states.amazonaws.com`, with an inline policy allowing `lambda:InvokeFunction`
   on `arn:aws:lambda:ap-southeast-2:ACCOUNT_ID:function:bp-*`.
3. **Backend user** with this policy:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "lambda:CreateFunction", "lambda:GetFunction", "lambda:UpdateFunctionCode",
           "lambda:UpdateFunctionConfiguration", "lambda:DeleteFunction",
           "lambda:CreateFunctionUrlConfig", "lambda:GetFunctionUrlConfig", "lambda:AddPermission"
         ],
         "Resource": "arn:aws:lambda:ap-southeast-2:ACCOUNT_ID:function:bp-*"
       },
       {
         "Effect": "Allow",
         "Action": ["states:CreateStateMachine", "states:UpdateStateMachine",
                    "states:DeleteStateMachine", "states:DescribeStateMachine"],
         "Resource": "arn:aws:states:ap-southeast-2:ACCOUNT_ID:stateMachine:bp-workflow-*"
       },
       {
         "Effect": "Allow",
         "Action": "iam:PassRole",
         "Resource": "arn:aws:iam::ACCOUNT_ID:role/bp-builder-lambda-exec",
         "Condition": { "StringEquals": { "iam:PassedToService": "lambda.amazonaws.com" } }
       },
       {
         "Effect": "Allow",
         "Action": "iam:PassRole",
         "Resource": "arn:aws:iam::ACCOUNT_ID:role/bp-builder-sfn-exec",
         "Condition": { "StringEquals": { "iam:PassedToService": "states.amazonaws.com" } }
       }
     ]
   }
   ```
4. Give the backend `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`
   (default `ap-southeast-2`), `LAMBDA_EXECUTION_ROLE_ARN` and
   `STEP_FUNCTIONS_ROLE_ARN`. `LAMBDA_RUNNER_JAR` and `LAMBDA_COMPONENT_JAR`
   optionally point to the two jars when the backend does not run from the
   repository.

A new function's jar is uploaded once (2.5 MB per component, 8 MB per entry
function); redeploying unchanged code skips the upload. Deployment does not
call any operation: whether a workflow runs is checked on the Test page. Function URLs are created without authentication, for testing
only: anyone with the URL can run the workflow.
