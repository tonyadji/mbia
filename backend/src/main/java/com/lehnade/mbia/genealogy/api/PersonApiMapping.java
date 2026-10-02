package com.lehnade.mbia.genealogy.api;

import com.lehnade.mbia.api.generated.model.DatePrecision;
import com.lehnade.mbia.api.generated.model.PartialDate;

/** Person values shared by the models of several operations (PersonResponse, TreeNode). */
final class PersonApiMapping {

    private PersonApiMapping() {}

    static PartialDate toApi(com.lehnade.mbia.genealogy.domain.PartialDate date) {
        return new PartialDate(DatePrecision.fromValue(date.precision().name()))
                .date(date.date())
                .year(date.year());
    }
}
