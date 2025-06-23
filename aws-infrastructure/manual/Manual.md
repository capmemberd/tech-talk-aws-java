# Content
- [Content](#content)
- [Create non-root user](#create-non-root-user)
- [Create VPC](#create-vpc)
- [Create EC2](#create-ec2-for-deployments)
- [Create DynamoDB](#create-dynamodb)
- [Create ECR](#create-ecr)
- [Create Load Balancer](#create-load-balancer)
- [Create ECS Fargate](#create-ecs-fargate)
- [Deploy our application](#deploy-our-application)
- [Manual clean-up](#manual-clean-up)

# Create non-root user
1. Go to AWS and login to your AWS account as root (using e-mail)
2. Go to AWS -> IAM
3. Go to Users and click on "Create user"

![](images/init_2.png)

4. Provide user details and click on "Next"

![](images/init_3.png)

Enable console access.
Do not require password reset at the next login.

5. Attach user to "Admins" user group and click on "Next"

![](images/init_4.png)

You could also attach **AdministrativeAccess** policy directly to the user.

**Attention!** Usually, we would like to carefully investigate which policies are actually needed for the user. However, in our case we really want to perform as an administrator, therefore, we are granting the administrative access policy to our user.

6. Review details and click on "Create user"

7. Copy Console-sign-in URL, save it in your browser and go back to the users list

![](images/init_5.png)

8. Select your user from the list and open its details
9. Open "Security credentials" tab

![](images/init_6.png)

10. Scroll down and click on "Create access key"

![](images/init_7.png)

11. Select CLI use case, confirm disclaimer and click on "Next"

![](images/init_8.png)

12. Specify some tag and click on "Create access key"

![](images/init_9.png)

13. Make sure that you have copied both **Access key** and **Secret access key** and stored securely, as we will later use them in AWS credentials file and in GitHub pipelines

![](images/init_10.png)

# Create VPC
1. Go to AWS -> VPC -> Create VPC
2. Select "VPC and more" to use the intuitive creator

IPv4 CIDR block can be set to 10.0.0.0/22. It will provide more than 1000 IP addresses which is more than enough for this lab.

No IPv6 CIDR is needed.

![](images/vpc_1.png)
![](images/vpc_2.png)

Choose 3 Availability Zones and leave other options as default.


Creator should create:
* Internet gateway
* Three private route tables (one for each private subnet)
* One public route table for all public subnets

3. Check created VPC -> Internet gateways

It provides access to the Internet.

4. Check VPC -> Route tables -> public route table

It contains route entry to the Internet gateway which makes all of the three subnets public.

![](images/vpc_3.png)
![](images/vpc_4.png)

5. Check VPC -> Route tables -> private route table

It contains prefix list with multiple IP addresses assigned to the S3 VPC Endpoint.

![](images/vpc_5.png)
![](images/vpc_6.png)

6. Check created VPC -> Endpoints

There should be a S3 VPC Endpoint providing internal access to S3 service from private subnets.

# Create EC2 for deployments
We will use this virtual machine for later deployments of our application.

1. Go to AWS -> EC2
2. Click on "Launch instance"
3. Fill name and select Amazon Linux as AMI

![](images/ec2_1.png)

4. Select t2.micro instance type and proceed without key pair, as we do not need SSH access for now

![](images/ec2_2.png)

5. Leave default settings for "Network settings".

We want to create EC2 instance in our **default VPC** to have a quick connection to it. Default VPC is always created automatically by AWS in our account and contains all stuff required to connect with the EC2 instance.

For this excercise we do not have to create EC2 in our previously created VPC.

6. Leave default values for "Configure storage"

AWS will automatically create a small storage.

7. Go into "Advanced details" and configure User data

```bash
sudo yum install git -y
sudo wget
http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo
-O /etc/yum.repos.d/epel-apache-maven.repo
sudo sed -i s/\$releasever/6/g /etc/yum.repos.d/epel-apache-maven.repo
sudo yum install maven -y
sudo yum install docker
sudo systemctl enable docker.service
sudo systemctl start docker.service
sudo chmod 666 /var/run/docker.sock
sudo yum install java-17-amazon-corretto-devel
```

![](images/ec2_8.png)

By doing this our EC2 instance will install packages that are needed for the deployment that we will perform at the end of the training.

8. Confirm by clicking on "Launch instance"

AWS will create additional resources on its own, such us security group, etc.

9.  Wait some time until instance is "Running"

It might take some time (~2 minutes).

10. Go to AWS -> IAM -> Roles and create new IAM role to allow EC2 instance to be able to push images to ECR later during deployment:
![](images/iam_5.png)

Set name and review created role:
![](images/iam_4.png)

Click on the "Create role".

11.  Go to AWS -> EC2 and assign the role to EC2 instance

![](images/ec2_6.png)
![](images/ec2_7.png)

12. Go to AWS -> EC2 and connect with the instance

![](images/ec2_4.png)
![](images/ec2_5.png)

# Create DynamoDB
1. Go to AWS -> DynamoDB
2. Click on "Create table"

![](images/dynamo_1.png)

3. Fill required table data

![](images/dynamo_2.png)

Table name should be set to "Measurements", as this is the table that our application will try to connect with.

Partition key must be set to "deviceId" (String) and sort key must be set to "creationTime" (Number).

4. Select default table settings and create table

![](images/dynamo_3.png)

# Create ECR
1. Go to AWS -> Elastic Container Registry and click on "Create repository"

![](images/ecr_1.png)

2. Fill basic details and click on "Create repository"

![](images/ecr_2.png)

# Create Load Balancer
1. Go to AWS -> EC2 -> Security Groups and click on "Create security group"

![](images/lb_1.png)

2. Add one "Inbound rule" to allow access from the Internet and click on "Create security group"

![](images/lb_2.png)

**Make sure that you have selected our custom VPC instead of the default one.**

3. Go to AWS -> EC2 -> Load Balancing -> Target groups and click on "Create target group"

![](images/lb_3.png)

4. Select "IP addresses" target type, specify name of your target group and select "HTTP" protocol and port 80

![](images/lb_4.png)

5. Select IPv4, VPC, Protocol version and setup health checks

![](images/lb_5.png)

**Make sure to select our custom VPC instead of the default one.**

```/actuator/health``` is the endpoint exposed by our application that provides health status.

6. Leave other attributes untouched and click on "Next"

7. In the next overview **do not specify any IP targets**, just click on "Create target group"

![](images/lb_6.png)

ECS Fargate will register IP addresses of its tasks to the target group on its own, we do not need to specify it manually.

8. Go to EC2 -> Load balancers and click on "Create load balancer"

![](images/lb_7.png)

9. Click on "Create" under Application Load Balancer

![](images/lb_8.png)

10. Fill initial details

![](images/lb_9.png)

We will be creating "Internet-facing" load balancer.

11. Configure "Network mapping"

![](images/lb_10.png)

**Make sure to select our custom VPC instead of the default one.**

Select all three AZs:

* eu-central-1a
* eu-central-1b
* eu-central-1c

Select only **public subnets** in each AZ.

12. Select previously created load balancer Security Group

![](images/lb_11.png)

13. Add listener by selecting previously created Target Group

![](images/lb_12.png)

Make sure that selected protocol is "HTTP" and port is 80.

14. Leave other values unchanged and click on "Create load balancer"

![](images/lb_13.png)

# Create ECS Fargate
1. Go to AWS -> Elastic Container Service and click on "Create cluster"

![](images/ecs_1.png)

2. Fill in cluster name, select "AWS Fargate (serverless)" as Infrastructure and click "Create"

![](images/ecs_2.png)

Wait until the creation has finished.

3. Go to AWS -> IAM -> Roles and click on "Create role"

![](images/ecs_3.png)

4. Select Elastic Container Service as "Service or use case" and Elatic Container Service Task as "Use case"

![](images/ecs_4.png)

With this configuration, ECS Task will be able to assume the role that we are now creating.

5. Do not specify any permissions policy, just click on "Next"

![](images/ecs_5.png)

We will specify inline policy and attach it to the role later.

6. In the "Name, review and create" view specify only role name and click on "Create role"

![](images/ecs_6.png)

First, we are creating ECS Task Role.

7. Repeat steps 3 - 6, but this tame create ECS Task **Execution** Role

![](images/ecs_7.png)

**The only difference (for now) will be the role name.**

8. In roles list first search for "task-role" and then select it

![](images/ecs_8.png)

9. Click on "Add permissions" and "Create inline policy"

![](images/ecs_9.png)

10. Select JSON editor, paste below policy and click on "Next"

![](images/ecs_10.png)

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "logs:*",
                "CloudWatch:*",
                "kinesis:*"
            ],
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Action": "dynamodb:*",
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Action": "ssm:GetParametersByPath",
            "Resource": [
                "arn:aws:ssm:eu-central-1:*:parameter/config/application*",
                "arn:aws:ssm:eu-central-1:*:parameter/config/backend*"
            ]
        }
    ]
}
```

11. Enter policy name and click on "Create policy"

![](images/ecs_11.png)

12.  Repeat steps 8 - 11, but this time for Task Execution Role and copy-paste the below policy

![](images/ecs_12.png)

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "logs:*",
                "ecr:GetDownloadUrlForLayer",
                "ecr:BatchGetImage",
                "ecr:GetAuthorizationToken",
                "CloudWatch:*",
                "ecr:BatchCheckLayerAvailability"
            ],
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Action": "secretsmanager:GetSecretValue",
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Action": "kms:Decrypt",
            "Effect": "Allow",
            "Resource": "*"
        }
    ]
}
```

13. Go to EC2 -> Security Groups and click on "Create security group"

![](images/lb_1.png)

14. Specify name, description, VPC and create one inbound rule to allow access from Load Balancer to Fargate and click on "Create security group"

![](images/ecs_13.png)

**Make sure to select our custom VPC instead of default one.**

Select previously created Load Balancer Security Group as "Source".

15. Go to AWS -> Elastic Container Service -> Task definitions and click on "Create new task definition"

![](images/ecs_14.png)

16. Specify name

![](images/ecs_15.png)

17. Select AWS Fargate as "Launch type", appropiate "Task size" and previously created "Task role" and "Task execution role"

![](images/ecs_16.png)

18. In a new tab, go to Elastic Container Registry and copy URI of your repository

![](images/ecs_17.png)

19. Go back to previous tab and fill Container details by setting red-marked values and leaving other values unchanged

![](images/ecs_18.png)

It is important to add **:latest** to the copied ECR URI, so that the most recent version of our application will be used when deploying new tasks.

20. Add two environmental variables that will tell the task in which environment it is running and, also, run our application with a proper profile

![](images/ecs_19.png)

21. Leave other attributes as default and click on "Create"

22. Go to "Clusters" (in left menu), select previously created Fargate cluster and under "Services" click on "Create"

![ecs_20](images/ecs_20.png)

23. Select "Launch type" simply as "Fargate"

![](images/ecs_21.png)

24. Specify "Deployment configuration"

![](images/ecs_22.png)

Initially, set "Desired tasks" to 0, as we do not want to start our service yet, as there is still no application image available in ECR and, therefore, we would receive errors.

25. Configure "Networking"

![](images/ecs_23.png)

**Make sure to select our custom VPC instead of default VPC.**

Select only public subnets. We do not want to select private subnets in this module, as then we would have to create VPC Endpoints to allow access to AWS services such as ECR from within tasks that were placed in the private subnets.

Select previously created Fargate Security Group.

Leave Public IP as turned on.

1.  Configure Load Balancer

![](images/ecs_24.png)

Select previously created Application Load Balancer and appropiate container.

27. Configure Listener & Target Group

![](images/ecs_25.png)

28. Leave other options as default and click on "Create"

# Deploy our application
1. Go to AWS -> EC2 and run our previously created instance

2. Clone repository using terminal

```bash
git clone https://github.com/capmemberd/tech-talk-aws-java.git
```

3. Go to awstraining-basics directory

```bash
cd awstraining-basics/
```

4. Build Java application using Maven

```bash
mvn clean install
```

5. Create Docker image

```bash
docker build -t myapp .
```

6. Go to AWS -> ECR -> your app repository and click on "View push commands"

![](images/deploy_7.png)

![](images/deploy_8.png)

7.   Login to AWS ECR by using the **ecr get-login-password** command from the pop-up dialog

```bash
aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin [YOUR_ACCOUNT_ID].dkr.ecr.eu-central-1.amazonaws.com
```

This will allow to push Docker images.

**When running locally - it is important to provide --profile option and specify your AWS profile.**

8. Tag Docker image with the **latest** tag

```bash
docker tag myapp:latest [YOUR_ACCOUNT_ID].dkr.ecr.eu-central-1.amazonaws.com/myapp:latest
```

9. Push image to ECR

```bash
docker push [YOUR_ACCOUNT_ID].dkr.ecr.eu-central-1.amazonaws.com/myapp:latest
```

10. Go to AWS -> ECR and confirm that the image was pushed

![](images/deploy_9.png)

11. Go to AWS -> ECS -> Your Fargate cluster -> select your service and click on "Update"

![](images/deploy_10.png)

12. Select "Force new deployment", specify "Desired tasks" to 3, leave other options untouched and click on "Update" button

![](images/deploy_11.png)

13. Verify if the deployment has started

![](images/deploy_12.png)

14. Under "Tasks" tab confirm that all 3 tasks are in state "Running"

![](images/deploy_13.png)

15. Wait some time (around 3 minutes) and then go to AWS -> EC2 -> Target groups, select your Target Group and confirm that all three tasks have been registered as "targets" with a "healthy" state

![](images/deploy_16.png)

16.  Go to AWS -> EC2 -> Load Balancers and select your Load Balancer

![](images/deploy_14.png)

17. Copy DNS of your Load Balancer

![](images/deploy_15.png)

18. Execute test request (just adjust URL to DNS of your Load Balancer)

Create test measurement using your Load Balancer DNS. Example:

```bash
curl -vk 'http://myapp-lb-564621670.eu-central-1.elb.amazonaws.com/device/v1/test' \
--header 'Content-Type: application/json' \
-u testUser:welt \
--data '{
    "type": "test",
    "value": -510.190
}'
```

Retrieve mesurements

```bash
curl -vk http://myapp-lb-564621670.eu-central-1.elb.amazonaws.com/device/v1/test -u testUser:welt
```

# Manual clean-up
Please clean up the following resources (order might be important):

* Application load balancer​
    * Listener, target group​
    * Load balancer itself​
* ECS​
* ECR​
* EC2​
* Elastic IP​
* DynamoDB​
* Lambda​
* S3​
* SNS​
* CloudWatch​
* VPC​
    * Subnets​
    * Route table​
    * Endpoints
    * VPC itself​

