package com.largatadev.timesheet.handoffs.dto;

import java.util.List;
import java.util.UUID;

/**
 * The create body once its JSON shape is read: the Report ids in order and the text. Either
 * may be null when the body omits it. There is no creator field — the creator is the JWT's.
 */
public record CreateHandoffRequest(List<UUID> reportIds, String text) {
}
