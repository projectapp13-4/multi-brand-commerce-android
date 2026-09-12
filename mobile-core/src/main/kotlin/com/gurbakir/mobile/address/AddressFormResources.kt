package com.gurbakir.mobile.address

import com.gurbakir.mobile.core.R

internal fun AddressFieldError.messageResource(): Int = when (this) {
    AddressFieldError.REQUIRED -> R.string.address_error_required
    AddressFieldError.INVALID_CHARACTERS -> R.string.address_error_invalid_characters
    AddressFieldError.TOO_LONG -> R.string.address_error_too_long
    AddressFieldError.INVALID_PHONE -> R.string.address_error_phone
    AddressFieldError.INVALID_POSTAL_CODE -> R.string.address_error_postal_code
    AddressFieldError.SERVER_REJECTED -> R.string.address_error_server_rejected
}

internal fun AddressFormFailure.messageResource(): Int = when (this) {
    AddressFormFailure.CONNECTION -> R.string.address_failure_connection
    AddressFormFailure.SERVICE -> R.string.address_failure_service
    AddressFormFailure.NOT_FOUND -> R.string.address_failure_not_found
    AddressFormFailure.UNSUPPORTED_COUNTRY -> R.string.address_failure_unsupported_country
    AddressFormFailure.CONFLICT -> R.string.address_failure_conflict
    AddressFormFailure.SAVE_UNCONFIRMED -> R.string.address_failure_unconfirmed
}
