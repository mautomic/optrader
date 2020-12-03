package com.mau.trading

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model.*
import com.mau.trading.utility.Constants

/**
 * A class that handles position and P&L communications via Amazon SES
 *
 * @author mautomic
 */
class Alerter(awsCredentials: AWSCredentials?, private val fromAddress: String, private val toAddresses: List<String>) {

    private val emailService: AmazonSimpleEmailService

    init {
        val credentialsProvider: AWSCredentialsProvider = AWSStaticCredentialsProvider(awsCredentials)
        emailService = AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(Regions.US_EAST_2).withCredentials(credentialsProvider).build()
    }

    /**
     * Sends an email with the given subject and body to the recipients
     */
    fun sendEmail(subject: String?, body: String?) {
        val request = SendEmailRequest()
                .withDestination(Destination().withToAddresses(toAddresses))
                .withMessage(Message()
                        .withBody(Body().withHtml(Content().withCharset(Constants.UTF8).withData(body)))
                        .withSubject(Content().withCharset(Constants.UTF8).withData(subject)))
                .withSource(fromAddress)
        emailService.sendEmail(request)
    }
}