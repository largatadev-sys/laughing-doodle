package com.largatadev.timesheet.reports;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** The reports enums are stored as the lowercase strings the migration's CHECK constraints
 * name (`problem`, `in_progress`, `ios`, …), the same convention as users.role. Grouped here
 * because all three are the identical one-line mapping — see {@link com.largatadev.timesheet.users.RoleConverter}. */
final class ReportEnumConverters {

	private ReportEnumConverters() {
	}

	private static String toColumn(Enum<?> value) {
		return value == null ? null : value.name().toLowerCase();
	}

	@Converter(autoApply = true)
	public static class ReportTypeConverter implements AttributeConverter<ReportType, String> {
		@Override
		public String convertToDatabaseColumn(ReportType value) {
			return toColumn(value);
		}

		@Override
		public ReportType convertToEntityAttribute(String dbValue) {
			return dbValue == null ? null : ReportType.valueOf(dbValue.toUpperCase());
		}
	}

	@Converter(autoApply = true)
	public static class ReportStatusConverter implements AttributeConverter<ReportStatus, String> {
		@Override
		public String convertToDatabaseColumn(ReportStatus value) {
			return toColumn(value);
		}

		@Override
		public ReportStatus convertToEntityAttribute(String dbValue) {
			return dbValue == null ? null : ReportStatus.valueOf(dbValue.toUpperCase());
		}
	}

	@Converter(autoApply = true)
	public static class PlatformConverter implements AttributeConverter<Platform, String> {
		@Override
		public String convertToDatabaseColumn(Platform value) {
			return toColumn(value);
		}

		@Override
		public Platform convertToEntityAttribute(String dbValue) {
			return dbValue == null ? null : Platform.valueOf(dbValue.toUpperCase());
		}
	}
}
