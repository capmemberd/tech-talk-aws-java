# AWS Training Reference Application
This repository holds reference Spring Boot project that can be deployed to AWS.

# Run locally
First run ```mvn clean install``` in root directory. Maven will generate Open API auto-generated classes.
Then, you should right-click on the **tech-talk-aws-java** in Project structure on the left and select 
**Maven -> Generate Sources & Update Folders**.

Then, please call ```docker-compose up``` in ```/local/assembly-local``` directory.

This will set up the following components:
* DynamoDB
  * With 'Measurements' table holding measurements for devices 
  * http://localhost:8000
* DynamoDB Admin Panel
  * http://localhost:8001
* Filebeat
  * It will load Spring Boot logs from file and redirect them to Elasticsearch
* Kibana
  * It will allow visual access to application logs 
  * http://localhost:5601
* Prometheus
  * It will allow querying application metrics
  * http://localhost:9090
* Grafana
  * It will allow dashboards creation
  * http://localhost:3003
* Elasticsearch
  * It will allow indexing and saving logs for later visual access via Kibana
  * http://localhost:9200

DynamoDB will be populated with test measurement data.

Then, please configure ```application.yml```:
```yml
aws:
  region: eu-central-1
  dynamodb:
    endpoint: http://localhost:8000
    accessKey: dummyAccess
    secretKey: dummySecret
```

We have to point to our local DynamoDB instance. Access and secret keys must be set to any values, they simply cannot 
stay empty.

Finally, simply run Application in IntelliJ with 'Run' button.

Kibana user is "elastic" and password is "changeme".

# Preparation to the deployment
To deploy infrastructure to your sandbox account please first fork our base repository.
To do it, go to:
* https://github.com/capmemberd/tech-talk-aws-java

and click on Fork button and then (+) Create new fork.

After forking repository to your account, please clone it to your local machine and search for all occurrences of:
* <<ACCOUNT_ID>>

This is base AWS account id that we use for the base repository.
You must replace this with your own account id in all files.

Then, you should go to **wrapper.properties** and set **UNIQUE_BUCKET_STRING** to your custom, unique string, that will
be added as a suffix to your state bucket name.

It is important to come up with a unique value, as this will affect the name of the Terraform state bucket 
that will be created, thus it must be unique globally. Please also do not make it too long.

E.g.:
* daja819ad

Push changes to your remote repository.

Then, in order to be able to apply Terraform changes locally, you should create a new profile 
in ```C:\Users\YOURUSER\.aws\credentials``` and set credentials to your account:
```
[backend-test]
aws_access_key_id = YOUR_ACCESS_KEY_ID
aws_secret_access_key = YOU_SECRET_ACCESS_KEY
```

**DO NOT USER ROOT USER CREDENTIALS!** Instead, create admin user in IAM, assign him **AdministratorAccess** policy
and generate credentials for this non-root user.

# Deploying AWS infrastructure (GitHub)
## Setting AWS credentials in GitHub
First you should go to **GitHub -> Your fork repo -> Settings -> Secrets and variables**
and create two repository secrets:
* BACKEND_EMEA_TEST_AWS_KEY
* BACKEND_EMEA_TEST_AWS_SECRET

and set accordingly **AWS_KEY** and **AWS_SECRET**, same as locally in ```..\.aws\credentials```.

## Running provisioning workflow
Then, you should run ```provisionWithTerraform``` pipeline under **Actions** tab.
This will automatically provision AWS infrastructure.

You can choose either **ecs** or **eks**, depending on which infrastructure you want to deploy.

## Configuring secrets in AWS
In order for our application to be able to access AWS Secrets Manager containing credentials for basic auth, please 
go to AWS Secret Manager, copy ARN of the created Secret and set it in the task definition for the region that you are deploying.

Search for (e.g. in EMEA-TEST tak definition, if you are deploying to EMEA TEST):
```
<<TODO: set ARN of secrets manager>>
```

Then, please go to AWS Secrets Manager, open your secrets and edit JSON string.
You should add the following secrets that will create users for basic auth:
```json
{
  "backend": {
    "security": {
      "users": [
        {
          "username": "userEMEATest",
          "password": "$2a$10$uKw9ORqCF.qA3p6woHCgmeGW0jFuU9AstYhl61Uw8RTQ5AaZCfuru",
          "roles": "USER"
        }
      ]
    }
  }
}
```

Spring will automatically load this JSON to the Spring container at the application start up and user **userEMEATest** 
with password **welt** will be available for basic auth during application execution in EMEA TEST environment.

### Setting basic auth credentials for tests
Remember to set **BACKEND_EMEA_TEST_SMOKETEST_BACKEND_PASSWORD** secret in GitHub Settings when using CICD workflow.
This should be set to "welt" (exactly like base-encoded password in AWS Secrets Manager), so that smoke tests will be executed
without issues in CICD.

## Build & Deploy to Fargate
When you are done with setting up the infrastructure, please go to your fork repository, open **Actions** tab and run
**Multibranch pipeline** on the main branch.

This branch will build Docker image, push it to ECR and deploy application to ECS Fargate.
After it has finished, you should go to your AWS account, open EC2 Load Balancers page and find
backend application load balancer.

Please then copy DNS of this load balancer and feel free to run test curls.
Example:
```
curl http://backend-lb-672995306.eu-central-1.elb.amazonaws.com/device/v1/test \
-u userEMEATest:welt
```

```
curl http://backend-lb-672995306.eu-central-1.elb.amazonaws.com/device/v1/test \
-H 'Content-Type: application/json' \
-u userEMEATest:welt \
--data '{
    "type": "testing",
    "value": -510.190
}'
```

User is **userEMEATest** and password is **welt**.

## Destroying infrastructure
In order to destroy your infrastructure you can simply call the **Destroy Infrastructure**
workflow in GitHub.

It will do the whole work for you.

Please remember to stop all running ECS Fargate tasks before executing the workflow.

# Applying Terraform changes for single module (locally)
## Using w2.sh script
In ```/aws-infrastructure/terraform``` directory:

```
./w2.sh [PROFILE] [REGION] [PATH_TO_MODULE] apply
```

For example:
```
./w2.sh backend-test eu-central-1 common/services/ecs-backend-cluster apply
```

## Using plain Terraform
1. Go to given directory
   ```cd aws-infrastructure/terraform/common/general/create-remote-state-bucket/```
2. Initiate terraform -> ``` terraform init ```. This will install all modules required by this configuration
3. Start creation of AWS infrastructure -> ``` terraform apply ```
4. When asked we need to provide some variables
```bash
var.common_tags             -> {"app:hub"="emea", "app:env"="test", "app:name"="backend", "terraform-path"="create-remote-state-bucket", terraform="true"}
var.environment             -> emea
var.profile                 -> backend-test
var.region                  -> eu-central-1
var.remote_state_bucket     -> tf-state-backend-test-eu-central-1-<<UNIQUE_BUCKET_STRING>>
var.shared_credentials_file -> C:\\Users\\<<USERNAME>>\\.aws\\credentials
```
Or use one-liner:
```bash
terraform apply \
  -var='common_tags={"app:hub"="emea", "app:env"="test", "app:name"="backend", "terraform-path"="create-remote-state-bucket", terraform="true"}' \
  -var='environment=emea' \
  -var='profile=backend-test' \
  -var='region=eu-central-1' \
  -var='remote_state_bucket=tf-state-backend-test-eu-central-1-<<UNIQUE_BUCKET_STRING>>' \
  -var='shared_credentials_file=C:\\Users\\<<USERNAME>>\\.aws\\credentials'
```
Set ```<<USERNAME>>``` to your CORP ID.

Unique bucket string is some string that will make your bucket name unique globally.

# Deploying AWS infrastructure (locally)
## Setting up the AWS infrastructure
You can also deploy infrastructure locally, without CICD.

To run Terraform you first need to install it on your local machine.
You need **terraform_1.4.6** or higher version.

Now you can run a script to set up a new AWS environment (still in ```/aws-infrastructure/terraform``` directory):
```
./setup_new_region.sh w2.sh backend-test eu-central-1 apply -auto-approve
```

Terraform should automatically approve all changes and create all required resources one-by-one.
In case of errors, please correct them, delete from setup_new_region.sh lines that has already been executed and run
the script again.

## Destroying AWS infrastructure
Stop all running tasks.

Run a script to destroy an AWS environment (in ```/aws-infrastructure/terraform``` directory):
```
./setup_new_region.sh w2.sh backend-test eu-central-1 destroy -auto-approve
```

Terraform should automatically approve all changes and delete all existing resources one-by-one.
In case of errors, please correct them, and run the script again.

Check IAM, Cloudwatch Logs, S3 buckets if everything was deleted.