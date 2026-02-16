import companies.models
from django.shortcuts import render
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status, permissions, serializers
from devices.serializers import (
    DeviceRegistrationSerializer,
    DeviceDataPostSerializer,
    DeviceAuthoritativeSerializer,
    DeviceListSerializer,
    DeviceManagementRequestSerializer,
    DeviceHistorySerializer,
    DeviceLoanPaymentSerializer,
    DeviceRecoveryKeySerializer,
    DeviceCategorySerializer,
    DeviceDeactivationRequestSerializer,
    DeviceDeactivationResponseSerializer,
    DeviceDeactivationConfirmSerializer,
    DeviceDeactivationConfirmResponseSerializer
)
from devices.permissions import CanRegisterDevice
from drf_spectacular.utils import extend_schema, OpenApiResponse, OpenApiExample
from drf_spectacular.types import OpenApiTypes
from rest_framework.permissions import AllowAny
from .models import Device, DeviceTemperSignal, DeviceHistory, DeviceManagement, DeviceCategory, DeviceCategoryField, DeviceHeartbeatHistory
from companies.models import Company
from rest_framework.permissions import IsAuthenticated
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework.generics import ListAPIView, RetrieveUpdateDestroyAPIView
from django.conf import settings
from django.db.models import Q
from django.utils.crypto import constant_time_compare
from drf_spectacular.utils import OpenApiParameter
from loans.models import Loan, LoanPayment
import json
import logging
from datetime import datetime

logger = logging.getLogger(__name__)


ALL_REGISTER_FIELDS = set(DeviceRegistrationSerializer.Meta.fields + ["loan_number"])
MOBILE_BASE_FIELDS = {
    "device_id",
    "loan_number",
    "device_type",
    "manufacturer",
    "model",
    "platform",
    "system_type",
    "os_version",
    "os_edition",
    "processor",
    "installed_ram",
    "total_storage",
    "build_number",
    "sdk_version",
    "system_uptime",
    "installed_apps_hash",
    "system_properties_hash",
    "tamper_severity",
    "tamper_flags",
    "battery_level",
    "language",
    "latitude",
    "longitude",
    "android_id",
    "bootloader",
    "security_patch_level",
}
MOBILE_RAW_FIELDS = {
    "loan_number",
    "device_id",
    "android_id",
    "model",
    "manufacturer",
    "brand",
    "product",
    "device",
    "board",
    "hardware",
    "build_id",
    "build_type",
    "build_tags",
    "build_time",
    "build_user",
    "build_host",
    "fingerprint",
    "bootloader",
    "android_info",
    "imei_info",
    "storage_info",
    "location_info",
    "app_info",
    "security_info",
    "system_integrity",
    # Optional legacy wrappers
    "device_info",
    "device_data",
    "registration_info",
    # Optional flat aliases
    "device_imeis",
    "imeis",
    "serial_number",
    "serial",
    "machine_name",
    "android_version",
}
DESKTOP_ALLOWED_FIELDS = {
    "device_id",
    "loan_number",
    "device_type",
    "manufacturer",
    "model",
    "platform",
    "system_type",
    "os_version",
    "os_edition",
    "processor",
    "installed_ram",
    "total_storage",
    "serial_number",
    "machine_name",
}
DESKTOP_SCHEMA_FIELDS = {
    "loan_number",
    "device_type",
    "manufacturer",
    "model",
    "platform",
    "system_type",
    "os_version",
    "os_edition",
    "processor",
    "installed_ram",
    "total_storage",
    "serial_number",
    "machine_name",
}
CATEGORY_BASE_FIELDS = {
    "device_id",
    "loan_number",
    "device_type",
    "manufacturer",
    "model",
    "platform",
    "system_type",
    "os_version",
    "os_edition",
    "processor",
    "installed_ram",
    "total_storage",
}
MOBILE_ONLY_FIELDS = {
    "device_imeis",
    "serial_number",
    "machine_name",
    "device_fingerprint",
    "is_device_rooted",
    "is_usb_debugging_enabled",
    "is_developer_mode_enabled",
    "is_bootloader_unlocked",
    "is_custom_rom",
}
DESKTOP_ONLY_FIELDS = {
    "disk_recovery_key",
}
MOBILE_TYPES = {"phone", "tablet"}
DESKTOP_TYPES = {"laptop", "desktop"}


logger = logging.getLogger(__name__)

DEVICE_API_HEADER_PARAM = OpenApiParameter(
    name="X-Device-Api-Key",
    location=OpenApiParameter.HEADER,
    required=True,
    type=OpenApiTypes.STR,
    description="Device agent API key (shared secret for desktop/laptop agents).",
)


def _require_device_api_key(request):
    expected = getattr(settings, "DEVICE_AGENT_API_KEY", "") or ""
    if not expected:
        return Response(
            {"success": False, "message": "Device API key not configured."},
            status=status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    header_name = getattr(settings, "DEVICE_AGENT_API_HEADER", "X-Device-Api-Key")
    provided = request.headers.get(header_name) or request.headers.get("X-Device-Api-Key") or request.headers.get("X-API-Key")
    client_ip = request.META.get("REMOTE_ADDR")
    user_agent = request.META.get("HTTP_USER_AGENT", "")
    if not provided:
        logger.warning(
            "Device agent auth failed: missing API key (header=%s, ip=%s, ua=%s)",
            header_name,
            client_ip,
            user_agent,
        )
        return Response(
            {
                "success": False,
                "message": "Device agent API key is required.",
                "hint": f"Send header {header_name}: <device-agent-key>",
            },
            status=status.HTTP_401_UNAUTHORIZED,
        )

    if not constant_time_compare(str(provided), str(expected)):
        logger.warning(
            "Device agent auth failed: invalid API key (header=%s, ip=%s, ua=%s)",
            header_name,
            client_ip,
            user_agent,
        )
        return Response(
            {
                "success": False,
                "message": "Invalid device agent API key.",
                "hint": f"Verify header {header_name} matches server key.",
            },
            status=status.HTTP_403_FORBIDDEN,
        )

    return None


class MobileDeviceRegistrationSchemaSerializer(DeviceRegistrationSerializer):
    class Meta(DeviceRegistrationSerializer.Meta):
        fields = sorted(MOBILE_BASE_FIELDS | MOBILE_ONLY_FIELDS)


class DesktopDeviceRegistrationSchemaSerializer(DeviceRegistrationSerializer):
    class Meta(DeviceRegistrationSerializer.Meta):
        fields = sorted(DESKTOP_SCHEMA_FIELDS | DESKTOP_ONLY_FIELDS)


class MobileRegistrationPayloadSerializer(serializers.Serializer):
    loan_number = serializers.CharField(required=True)
    device_id = serializers.CharField(required=False)
    android_id = serializers.CharField(required=False)
    model = serializers.CharField(required=False)
    manufacturer = serializers.CharField(required=False)
    brand = serializers.CharField(required=False)
    product = serializers.CharField(required=False)
    device = serializers.CharField(required=False)
    board = serializers.CharField(required=False)
    hardware = serializers.CharField(required=False)
    build_id = serializers.CharField(required=False)
    build_type = serializers.CharField(required=False)
    build_tags = serializers.CharField(required=False)
    build_time = serializers.IntegerField(required=False)
    build_user = serializers.CharField(required=False)
    build_host = serializers.CharField(required=False)
    fingerprint = serializers.CharField(required=False)
    bootloader = serializers.CharField(required=False)
    android_info = serializers.DictField(required=False)
    imei_info = serializers.DictField(required=False)
    storage_info = serializers.DictField(required=False)
    location_info = serializers.DictField(required=False)
    app_info = serializers.DictField(required=False)
    security_info = serializers.DictField(required=False)
    system_integrity = serializers.DictField(required=False)

def _reject_unexpected_fields(payload, allowed_fields):
    extra = [k for k in payload.keys() if k not in allowed_fields]
    if extra:
        return Response(
            {
                "success": False,
                "message": "Unexpected fields for this device category.",
                "extra_fields": sorted(extra),
            },
            status=status.HTTP_400_BAD_REQUEST,
        )
    return None


def _normalize_mobile_payload(payload):
    """
    Accept nested mobile payload shapes and map them into flat fields
    expected by the mobile register endpoint. Unknown fields are dropped
    to avoid "Unexpected fields" errors.
    
    Also normalizes storage values to ensure consistent formatting.
    """
    data = payload.copy()

    # Common wrappers used by agents
    registration_info = data.get("registration_info") or {}
    device_info = data.get("device_info") or data.get("device_data") or {}
    android_info = data.get("android_info") or {}
    imei_info = data.get("imei_info") or {}
    storage_info = data.get("storage_info") or {}
    location_info = data.get("location_info") or {}
    security_info = data.get("security_info") or {}
    system_integrity = data.get("system_integrity") or {}

    normalized = {}

    # Loan number
    normalized["loan_number"] = (
        data.get("loan_number")
        or device_info.get("loan_number")
        or registration_info.get("loan_number")
    )

    # Device identity
    normalized["device_id"] = data.get("device_id") or device_info.get("device_id")
    normalized["android_id"] = (
        data.get("android_id")
        or device_info.get("android_id")
        or device_info.get("device_id")
    )
    normalized["model"] = data.get("model") or device_info.get("model")
    normalized["manufacturer"] = data.get("manufacturer") or device_info.get("manufacturer")

    # Optional hardware + system info
    normalized["platform"] = data.get("platform") or device_info.get("hardware") or data.get("hardware")
    normalized["system_type"] = data.get("system_type") or device_info.get("device") or data.get("device")
    normalized["os_version"] = data.get("os_version") or data.get("android_version") or android_info.get("version_release")
    normalized["os_edition"] = data.get("os_edition") or android_info.get("version_incremental")
    normalized["processor"] = data.get("processor") or device_info.get("hardware") or data.get("hardware")
    
    # NORMALIZE storage values - remove spaces for consistency
    installed_ram = data.get("installed_ram") or storage_info.get("installed_ram")
    if installed_ram:
        normalized["installed_ram"] = str(installed_ram).replace(" ", "")
    
    total_storage = data.get("total_storage") or storage_info.get("total_storage")
    if total_storage:
        normalized["total_storage"] = str(total_storage).replace(" ", "")

    # Build and integrity
    normalized["build_number"] = data.get("build_number")
    normalized["sdk_version"] = data.get("sdk_version") or android_info.get("version_sdk_int")
    normalized["device_fingerprint"] = data.get("device_fingerprint") or device_info.get("fingerprint") or data.get("fingerprint")
    normalized["bootloader"] = data.get("bootloader") or device_info.get("bootloader")
    normalized["security_patch_level"] = data.get("security_patch_level") or android_info.get("security_patch")
    normalized["system_uptime"] = data.get("system_uptime")
    normalized["installed_apps_hash"] = data.get("installed_apps_hash") or system_integrity.get("installed_apps_hash")
    normalized["system_properties_hash"] = data.get("system_properties_hash") or system_integrity.get("system_properties_hash")

    # Security flags
    normalized["is_device_rooted"] = data.get("is_device_rooted") or security_info.get("is_device_rooted")
    normalized["is_usb_debugging_enabled"] = data.get("is_usb_debugging_enabled") or security_info.get("is_usb_debugging_enabled")
    normalized["is_developer_mode_enabled"] = data.get("is_developer_mode_enabled") or security_info.get("is_developer_mode_enabled")
    normalized["is_bootloader_unlocked"] = data.get("is_bootloader_unlocked") or security_info.get("is_bootloader_unlocked")
    normalized["is_custom_rom"] = data.get("is_custom_rom") or security_info.get("is_custom_rom")

    # Location + battery + language
    normalized["latitude"] = data.get("latitude") or location_info.get("latitude")
    normalized["longitude"] = data.get("longitude") or location_info.get("longitude")
    normalized["battery_level"] = data.get("battery_level")
    normalized["language"] = data.get("language")

    # IMEIs - normalize to list format
    # CRITICAL: Check for None explicitly, not truthiness (empty list [] is falsy!)
    imeis = data.get("device_imeis") or data.get("imeis") or device_info.get("imeis") or imei_info.get("device_imeis")
    
    if imeis is not None:  # Explicit None check
        if isinstance(imeis, str):
            normalized["device_imeis"] = [imeis]
        elif isinstance(imeis, list):
            # If list is empty, mark as tablet/WiFi-only
            normalized["device_imeis"] = imeis if imeis else ["NO_IMEI_FOUND"]
        else:
            normalized["device_imeis"] = [str(imeis)]
    else:
        # If no IMEI provided at all, mark as tablet/WiFi-only
        normalized["device_imeis"] = ["NO_IMEI_FOUND"]

    # Optional serial/machine names if sent
    normalized["serial_number"] = data.get("serial_number") or data.get("serial") or device_info.get("serial")
    normalized["machine_name"] = data.get("machine_name") or device_info.get("machine_name")

    # Keep any already-flat allowed fields that were sent
    for key in MOBILE_BASE_FIELDS | MOBILE_ONLY_FIELDS:
        if key in data and key not in normalized:
            normalized[key] = data.get(key)

    # Drop empty values
    return {k: v for k, v in normalized.items() if v is not None and v != ""}


def _normalize_mobile_heartbeat(payload):
    data = payload.copy()

    android_info = data.get("android_info") or {}
    imei_info = data.get("imei_info") or {}
    storage_info = data.get("storage_info") or {}
    location_info = data.get("location_info") or {}
    security_info = data.get("security_info") or {}
    system_integrity = data.get("system_integrity") or {}
    app_info = data.get("app_info") or {}

    normalized = {}
    normalized["android_id"] = data.get("android_id")
    normalized["model"] = data.get("model")
    normalized["manufacturer"] = data.get("manufacturer")
    normalized["serial_number"] = data.get("serial") or data.get("serial_number")
    normalized["device_fingerprint"] = data.get("fingerprint")
    normalized["bootloader"] = data.get("bootloader")

    normalized["os_version"] = data.get("os_version") or android_info.get("version_release")
    normalized["os_edition"] = data.get("os_edition") or android_info.get("version_incremental")
    normalized["sdk_version"] = data.get("sdk_version") or android_info.get("version_sdk_int")
    normalized["security_patch_level"] = data.get("security_patch_level") or android_info.get("security_patch")

    # NORMALIZE storage values - remove spaces for consistency
    installed_ram = data.get("installed_ram") or storage_info.get("installed_ram")
    if installed_ram:
        normalized["installed_ram"] = str(installed_ram).replace(" ", "")
    
    total_storage = data.get("total_storage") or storage_info.get("total_storage")
    if total_storage:
        normalized["total_storage"] = str(total_storage).replace(" ", "")

    # NORMALIZE IMEIs - ensure list format
    # CRITICAL: Check for None explicitly, not truthiness (empty list [] is falsy!)
    device_imeis = data.get("device_imeis") or imei_info.get("device_imeis")
    if device_imeis is not None:  # Explicit None check
        if isinstance(device_imeis, str):
            normalized["device_imeis"] = [device_imeis]
        elif isinstance(device_imeis, list):
            normalized["device_imeis"] = device_imeis if device_imeis else ["NO_IMEI_FOUND"]
        else:
            normalized["device_imeis"] = [str(device_imeis)]
    else:
        normalized["device_imeis"] = ["NO_IMEI_FOUND"]

    normalized["latitude"] = data.get("latitude") or location_info.get("latitude")
    normalized["longitude"] = data.get("longitude") or location_info.get("longitude")

    normalized["installed_apps_hash"] = data.get("installed_apps_hash") or system_integrity.get("installed_apps_hash")
    normalized["system_properties_hash"] = data.get("system_properties_hash") or system_integrity.get("system_properties_hash")

    normalized["is_device_rooted"] = data.get("is_device_rooted") or security_info.get("is_device_rooted")
    normalized["is_usb_debugging_enabled"] = data.get("is_usb_debugging_enabled") or security_info.get("is_usb_debugging_enabled")
    normalized["is_developer_mode_enabled"] = data.get("is_developer_mode_enabled") or security_info.get("is_developer_mode_enabled")
    normalized["is_bootloader_unlocked"] = data.get("is_bootloader_unlocked") or security_info.get("is_bootloader_unlocked")
    normalized["is_custom_rom"] = data.get("is_custom_rom") or security_info.get("is_custom_rom")

    normalized["battery_level"] = data.get("battery_level")

    # Drop empty values
    normalized = {k: v for k, v in normalized.items() if v is not None and v != ""}

    extra = {
        "brand": data.get("brand"),
        "product": data.get("product"),
        "device": data.get("device"),
        "board": data.get("board"),
        "hardware": data.get("hardware"),
        "build_id": data.get("build_id"),
        "build_type": data.get("build_type"),
        "build_tags": data.get("build_tags"),
        "build_time": data.get("build_time"),
        "build_user": data.get("build_user"),
        "build_host": data.get("build_host"),
        "fingerprint": data.get("fingerprint"),
        "android_info": android_info,
        "imei_info": imei_info,
        "storage_info": storage_info,
        "location_info": location_info,
        "app_info": app_info,
        "security_info": security_info,
        "system_integrity": system_integrity,
    }

    extra = {k: v for k, v in extra.items() if v is not None and v != ""}
    return normalized, extra


def _ensure_category_allowed(loan, category):
    if not loan or not category:
        return Response({"success": False, "message": "Invalid loan or category."}, status=status.HTTP_400_BAD_REQUEST)
    shop = getattr(loan, "shop", None)
    if not shop or not getattr(shop, "user", None):
        return Response({"success": False, "message": "Loan has no shop owner."}, status=status.HTTP_400_BAD_REQUEST)

    allowed = getattr(shop.user, "device_categories", None)
    if allowed is None:
        return None

    if allowed.exists() and not allowed.filter(id=category.id).exists():
        return Response(
            {"success": False, "message": "Shop owner is not allowed to register this device category."},
            status=status.HTTP_403_FORBIDDEN,
        )
    return None


def _schema_for_category(*, category_slug, company, category=None):
    if category_slug == "mobile":
        fields = sorted(MOBILE_RAW_FIELDS)
    elif category_slug == "desktop":
        fields = sorted(DESKTOP_SCHEMA_FIELDS | DESKTOP_ONLY_FIELDS)
    else:
        category_fields = set(category.fields.values_list("field_name", flat=True)) if category else set()
        fields = sorted(CATEGORY_BASE_FIELDS | category_fields)

    example = {k: None for k in fields}
    example["loan_number"] = "string"
    if category_slug == "mobile":
        example = {
            "loan_number": "string",
            "device_id": "string",
            "android_id": "string",
            "model": "string",
            "manufacturer": "string",
            "brand": "string",
            "product": "string",
            "device": "string",
            "board": "string",
            "hardware": "string",
            "build_id": "string",
            "build_type": "string",
            "build_tags": "string",
            "build_time": 0,
            "build_user": "string",
            "build_host": "string",
            "fingerprint": "string",
            "bootloader": "string",
            "android_info": {
                "version_release": "string",
                "version_sdk_int": 0,
                "version_codename": "string",
                "version_incremental": "string",
                "security_patch": "string"
            },
            "imei_info": {
                "phone_count": 0
            },
            "storage_info": {
                "total_storage": "string",
                "installed_ram": "string"
            },
            "location_info": {},
            "app_info": {
                "package_name": "string",
                "data_dir": "string",
                "source_dir": "string"
            },
            "security_info": {
                "is_device_rooted": False,
                "is_usb_debugging_enabled": False,
                "is_developer_mode_enabled": False,
                "is_bootloader_unlocked": False,
                "is_custom_rom": False
            },
            "system_integrity": {
                "installed_apps_hash": "string",
                "system_properties_hash": "string"
            }
        }
    elif category_slug == "desktop":
        example["device_type"] = "laptop"
    return {
        "company_id": company.id if company else None,
        "category": category_slug,
        "endpoint": f"/api/devices/{category_slug}/register/",
        "fields": fields,
        "example_payload": example,
    }

def _ensure_default_categories(company):
    defaults = [("mobile", "Mobile"), ("desktop", "Desktop")]
    for name, display in defaults:
        DeviceCategory.objects.get_or_create(
            company=company,
            name=name,
            defaults={"display_name": display, "is_system": True},
        )


def _resolve_category_for_device_type(company, device_type):
    if not company:
        return None
    _ensure_default_categories(company)
    if device_type in ["phone", "tablet"]:
        return DeviceCategory.objects.filter(company=company, name="mobile").first()
    if device_type in ["laptop", "desktop"]:
        return DeviceCategory.objects.filter(company=company, name="desktop").first()
    return None


def _coerce_category_value(field, value):
    ftype = field.field_type
    if ftype == "string":
        value = "" if value is None else str(value)
        if field.max_length and len(value) > field.max_length:
            raise ValueError(f"max length {field.max_length}")
        return value
    if ftype == "integer":
        iv = int(value)
        if field.min_value is not None and iv < float(field.min_value):
            raise ValueError(f"min {field.min_value}")
        if field.max_value is not None and iv > float(field.max_value):
            raise ValueError(f"max {field.max_value}")
        return iv
    if ftype == "float":
        fv = float(value)
        if field.min_value is not None and fv < float(field.min_value):
            raise ValueError(f"min {field.min_value}")
        if field.max_value is not None and fv > float(field.max_value):
            raise ValueError(f"max {field.max_value}")
        return fv
    if ftype == "boolean":
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            v = value.strip().lower()
            if v in ["true", "1", "yes"]:
                return True
            if v in ["false", "0", "no"]:
                return False
        raise ValueError("invalid boolean")
    if ftype == "list":
        if isinstance(value, list):
            return value
        raise ValueError("invalid list")
    if ftype == "json":
        if isinstance(value, (dict, list)):
            return value
        raise ValueError("invalid json")
    return value


def _extract_category_data(category, payload):
    data = {}
    errors = {}
    if not category:
        return data, errors

    for field in category.fields.all():
        key = field.field_name
        has_key = key in payload
        raw = payload.get(key) if has_key else None

        if (raw is None or raw == "") and field.required:
            errors[key] = "This field is required."
            continue

        if not has_key or raw is None or raw == "":
            continue

        try:
            data[key] = _coerce_category_value(field, raw)
        except Exception as exc:
            errors[key] = str(exc)

    return data, errors


class DeviceCategoryRegistrationView(APIView):
    permission_classes = [AllowAny]
    category_slug = None

    @extend_schema(
        tags=["Devices"],
        description="Register a device for a loan (requires device agent API key).",
        parameters=[DEVICE_API_HEADER_PARAM],
        request=DeviceRegistrationSerializer,
        responses={
            201: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device registered"),
            401: OpenApiResponse(description="Missing API key"),
            403: OpenApiResponse(description="Invalid API key"),
        },
        examples=[
            OpenApiExample(
                "Mobile payload (minimal)",
                value={
                    "loan_number": "LN-20260131-00001",
                    "device_id": "ANDROID-123",
                    "device_type": "phone",
                    "manufacturer": "Samsung",
                    "model": "A14",
                    "android_id": "abcd1234",
                },
                request_only=True,
            ),
            OpenApiExample(
                "Desktop payload (minimal)",
                value={
                    "loan_number": "LN-20260131-00001",
                    "device_id": "DESKTOP-123",
                    "device_type": "laptop",
                    "manufacturer": "Dell",
                    "model": "Latitude 7420",
                    "serial_number": "SN-ABC-123",
                    "machine_name": "LAPTOP-01",
                },
                request_only=True,
            ),
        ],
    )
    def post(self, request, *args, **kwargs):
        deny = _require_device_api_key(request)
        if deny:
            return deny

        slug = self.category_slug or kwargs.get("category_slug")
        if not slug:
            return Response({"success": False, "message": "category is required."}, status=status.HTTP_400_BAD_REQUEST)

        data = request.data.copy()
        if "device_id" in data:
            data.pop("device_id", None)
        
        # NORMALIZE FIRST before any field validation
        if slug == "mobile":
            data = _normalize_mobile_payload(data)
            if "device_type" not in data:
                data["device_type"] = "phone"
            if data.get("device_type") in DESKTOP_TYPES:
                return Response({"success": False, "message": "Desktop device_type not allowed on mobile endpoint."}, status=status.HTTP_400_BAD_REQUEST)
        elif slug == "desktop":
            if "device_type" not in data:
                data["device_type"] = "desktop"
            if data.get("device_type") in MOBILE_TYPES:
                return Response({"success": False, "message": "Mobile device_type not allowed on desktop endpoint."}, status=status.HTTP_400_BAD_REQUEST)

        # NOW validate fields after normalization
        if slug == "mobile":
            allowed = MOBILE_BASE_FIELDS | MOBILE_ONLY_FIELDS
            bad = _reject_unexpected_fields(data, allowed)
            if bad:
                return bad
        elif slug == "desktop":
            allowed = DESKTOP_ALLOWED_FIELDS | DESKTOP_ONLY_FIELDS
            bad = _reject_unexpected_fields(data, allowed)
            if bad:
                return bad

        serializer = DeviceRegistrationSerializer(data=data, context={"request": request})
        if serializer.is_valid():
            try:
                loan = serializer.context.get("loan")
                if not loan:
                    return Response({"success": False, "message": "Loan information is required."}, status=status.HTTP_400_BAD_REQUEST)

                _ensure_default_categories(loan.company)
                category = DeviceCategory.objects.filter(company=loan.company, name=slug).first()
                if not category:
                    return Response(
                        {"success": False, "message": f"Device category '{slug}' not found for company."},
                        status=status.HTTP_400_BAD_REQUEST
                    )

                if loan.device_category_id and loan.device_category_id != category.id:
                    return Response(
                        {
                            "success": False,
                            "message": (
                                "Loan is locked to a different device category. "
                                f"Expected '{loan.device_category.name}'."
                            ),
                        },
                        status=status.HTTP_400_BAD_REQUEST,
                    )

                deny = _ensure_category_allowed(loan, category)
                if deny:
                    return deny

                # For built-in categories (mobile/desktop), use their specific field sets
                # For custom categories, use the category-defined fields
                if slug == "mobile":
                    allowed = MOBILE_BASE_FIELDS | MOBILE_ONLY_FIELDS
                elif slug == "desktop":
                    allowed = DESKTOP_ALLOWED_FIELDS | DESKTOP_ONLY_FIELDS
                else:
                    category_field_names = set(category.fields.values_list("field_name", flat=True))
                    allowed = CATEGORY_BASE_FIELDS | category_field_names
                
                bad = _reject_unexpected_fields(data, allowed)
                if bad:
                    return bad

                if "device_type" not in data:
                    data["device_type"] = "other"

                category_data, errors = _extract_category_data(category, data)
                if errors:
                    return Response(
                        {"success": False, "message": "Invalid category data.", "errors": errors},
                        status=status.HTTP_400_BAD_REQUEST
                    )

                serializer.context["device_category"] = category
                serializer.context["category_data"] = category_data

                device = serializer.save()
                
                # CRITICAL: Save complete registration payload for audit trail
                # This ensures ALL data sent by device is preserved
                device.registration_payload = {
                    "timestamp": timezone.now().isoformat(),
                    "device_id": device.device_id,
                    "loan_number": loan.loan_number,
                    "category": slug,
                    "raw_payload": request.data.copy(),  # Original nested structure
                    "normalized_payload": data,  # Flattened structure
                    "extracted_fields": {
                        "device_info": request.data.get("device_info", {}),
                        "android_info": request.data.get("android_info", {}),
                        "imei_info": request.data.get("imei_info", {}),
                        "storage_info": request.data.get("storage_info", {}),
                        "location_info": request.data.get("location_info", {}),
                        "security_info": request.data.get("security_info", {}),
                        "system_integrity": request.data.get("system_integrity", {}),
                        "app_info": request.data.get("app_info", {}),
                    }
                }
                device.save(update_fields=["registration_payload"])
                
                logger.info(f"✅ Device {device.device_id} registered with complete payload stored")

                # Return all posted data + server-assigned device_id
                # This way the app gets confirmation of what was registered
                if slug == "desktop":
                    data["device_id"] = device.device_id

                response_data = {
                    "success": True,
                    "message": "Device registered successfully",
                    "data": data  # All posted data + device_id
                }

                if slug == "mobile":
                    response_data["device_id"] = device.device_id

                return Response(response_data, status=status.HTTP_201_CREATED)
            except Exception as e:
                print(f"Device registration error: {str(e)}")
                return Response(
                    {
                        "success": False,
                        "message": "Failed to register device. Please contact support.",
                        "error": str(e) if settings.DEBUG else None
                    },
                    status=status.HTTP_500_INTERNAL_SERVER_ERROR
                )

        errors = serializer.errors
        if "loan_number" in errors:
            # Use the actual validation message (e.g. "Loan not found", "Cannot register for completed loan")
            loan_errors = errors["loan_number"]
            msg = loan_errors[0] if isinstance(loan_errors, list) and loan_errors else str(loan_errors)
            return Response(
                {
                    "success": False,
                    "message": msg,
                    "field": "loan_number",
                    "errors": {"loan_number": loan_errors},
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        return Response(
            {
                "success": False,
                "message": "Registration failed. Please check all fields.",
                "errors": errors,
            },
            status=status.HTTP_400_BAD_REQUEST,
        )


@extend_schema(
    tags=["Device Registration"],
    summary="Register mobile device",
    request=MobileRegistrationPayloadSerializer,
    responses={
        201: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device registered successfully"),
        400: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Invalid data"),
        500: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Server error"),
    },
)
@extend_schema(
    tags=["Devices"],
    description="Register a mobile device for a loan (requires device agent API key).",
    parameters=[DEVICE_API_HEADER_PARAM],
    request=MobileDeviceRegistrationSchemaSerializer,
    responses={
        201: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device registered"),
        401: OpenApiResponse(description="Missing API key"),
        403: OpenApiResponse(description="Invalid API key"),
    },
    examples=[
        OpenApiExample(
            "Mobile payload (minimal)",
            value={
                "loan_number": "LN-20260131-00001",
                "device_id": "ANDROID-123",
                "device_type": "phone",
                "manufacturer": "Samsung",
                "model": "A14",
                "android_id": "abcd1234",
            },
            request_only=True,
        ),
    ],
)
class MobileDeviceRegistrationView(DeviceCategoryRegistrationView):
    category_slug = "mobile"


@extend_schema(
    tags=["Device Registration"],
    summary="Register desktop device",
    request=DesktopDeviceRegistrationSchemaSerializer,
    responses={
        201: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device registered successfully"),
        400: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Invalid data"),
        500: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Server error"),
    },
)
@extend_schema(
    tags=["Devices"],
    description="Register a desktop/laptop device for a loan (requires device agent API key).",
    parameters=[DEVICE_API_HEADER_PARAM],
    request=DesktopDeviceRegistrationSchemaSerializer,
    responses={
        201: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device registered"),
        401: OpenApiResponse(description="Missing API key"),
        403: OpenApiResponse(description="Invalid API key"),
    },
    examples=[
        OpenApiExample(
            "Desktop payload (minimal)",
            value={
                "loan_number": "LN-20260131-00001",
                "device_id": "DESKTOP-123",
                "device_type": "laptop",
                "manufacturer": "Dell",
                "model": "Latitude 7420",
                "serial_number": "SN-ABC-123",
                "machine_name": "LAPTOP-01",
            },
            request_only=True,
        ),
    ],
)
class DesktopDeviceRegistrationView(DeviceCategoryRegistrationView):
    category_slug = "desktop"


@extend_schema(tags=["Device Categories"], responses={200: DeviceCategorySerializer(many=True)})
class DeviceCategoryListCreateView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        if request.user.is_superuser:
            qs = DeviceCategory.objects.all().order_by("name", "id")
            seen = set()
            unique = []
            for item in qs:
                if item.name in seen:
                    continue
                seen.add(item.name)
                unique.append(item)
            return Response(DeviceCategorySerializer(unique, many=True).data)
        else:
            if not getattr(request.user, "company_id", None):
                return Response([], status=status.HTTP_200_OK)
            qs = DeviceCategory.objects.filter(company=request.user.company)

        return Response(DeviceCategorySerializer(qs, many=True).data)

    def post(self, request):
        if not request.user.is_superuser:
            return Response({"error": "Permission denied."}, status=status.HTTP_403_FORBIDDEN)

        serializer = DeviceCategorySerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        fields_data = data.pop("fields", [])
        name = data.get("name")
        display_name = data.get("display_name")

        companies_qs = companies.models.Company.objects.all()
        if not companies_qs.exists():
            return Response({"error": "No companies found."}, status=status.HTTP_400_BAD_REQUEST)

        created = []
        for company in companies_qs:
            category, created_flag = DeviceCategory.objects.get_or_create(
                company=company,
                name=name,
                defaults={
                    "display_name": display_name,
                    "is_system": False,
                    "created_by": request.user,
                },
            )

            if created_flag:
                for idx, field in enumerate(fields_data):
                    field.setdefault("order", idx)
                    DeviceCategoryField.objects.create(category=category, **field)
            created.append(category)

        return Response(DeviceCategorySerializer(created[0]).data, status=status.HTTP_201_CREATED)


@extend_schema(tags=["Device Categories"])
class DeviceCategoryDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def patch(self, request, category_id):
        if not request.user.is_superuser:
            return Response({"error": "Permission denied."}, status=status.HTTP_403_FORBIDDEN)

        category = DeviceCategory.objects.filter(id=category_id).first()
        if not category:
            return Response({"error": "Not found."}, status=status.HTTP_404_NOT_FOUND)

        if category.is_system and "name" in request.data and request.data.get("name") != category.name:
            return Response({"error": "Cannot rename system category."}, status=status.HTTP_400_BAD_REQUEST)

        targets = DeviceCategory.objects.filter(name=category.name)
        serializer = DeviceCategorySerializer(category, data=request.data, partial=True)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        payload = serializer.validated_data
        fields_data = payload.pop("fields", None)

        updated = []
        for item in targets:
            for key, value in payload.items():
                setattr(item, key, value)
            item.save()
            if fields_data is not None:
                item.fields.all().delete()
                for idx, field in enumerate(fields_data):
                    field.setdefault("order", idx)
                    DeviceCategoryField.objects.create(category=item, **field)
            updated.append(item)

        return Response(DeviceCategorySerializer(updated[0]).data)

    def delete(self, request, category_id):
        if not request.user.is_superuser:
            return Response({"error": "Permission denied."}, status=status.HTTP_403_FORBIDDEN)

        category = DeviceCategory.objects.filter(id=category_id).first()
        if not category:
            return Response({"error": "Not found."}, status=status.HTTP_404_NOT_FOUND)

        if category.is_system:
            return Response({"error": "Cannot delete system category."}, status=status.HTTP_400_BAD_REQUEST)

        DeviceCategory.objects.filter(name=category.name).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


@extend_schema(tags=["Device Categories"])
class CompanyAllowedDeviceCategoriesView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        company = getattr(request.user, "company", None)

        if not company:
            return Response({"error": "Company not found."}, status=status.HTTP_404_NOT_FOUND)

        categories = company.allowed_device_categories.all()
        data = [
            {"id": c.id, "name": c.name, "display_name": c.display_name}
            for c in categories
        ]
        return Response({"company_id": company.id, "categories": data})

    def patch(self, request):
        if not (request.user.is_superuser or getattr(request.user, "is_company_admin", False)):
            return Response({"error": "Permission denied."}, status=status.HTTP_403_FORBIDDEN)

        category_ids = request.data.get("category_ids", [])

        company = getattr(request.user, "company", None)

        if not company:
            return Response({"error": "Company not found."}, status=status.HTTP_404_NOT_FOUND)

        if not isinstance(category_ids, list):
            return Response({"error": "category_ids must be a list."}, status=status.HTTP_400_BAD_REQUEST)

        valid = DeviceCategory.objects.filter(company=company, id__in=category_ids)
        if valid.count() != len(set(category_ids)):
            return Response({"error": "Invalid category_ids for this company."}, status=status.HTTP_400_BAD_REQUEST)

        company.allowed_device_categories.set(valid)
        return Response({"company_id": company.id, "categories": [
            {"id": c.id, "name": c.name, "display_name": c.display_name} for c in valid
        ]})


@extend_schema(tags=["Device Categories"])
class DeviceCategorySchemaView(APIView):
    permission_classes = [AllowAny]

    def get(self, request, category_slug):
        company = None
        if request.user.is_authenticated:
            company = getattr(request.user, "company", None)

        if category_slug in ["mobile", "desktop"]:
            return Response(_schema_for_category(category_slug=category_slug, company=company))

        category = None
        if company:
            category = DeviceCategory.objects.filter(company=company, name=category_slug).first()
        if not category:
            category = DeviceCategory.objects.filter(name=category_slug).order_by("id").first()
        if not category:
            return Response({"error": "Category not found for this company."}, status=status.HTTP_404_NOT_FOUND)

        return Response(_schema_for_category(category_slug=category_slug, company=category.company, category=category))


@extend_schema(
    tags=["Device Management"],
    request=DeviceRegistrationSerializer,
    responses={201: DeviceRegistrationSerializer}
)
class DeviceStatusView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, device_id):
        try:
            try:
                device = Device.objects.select_related('management', 'company', 'shop', 'borrowed_by').get(
                    device_id=device_id
                )
            except Device.DoesNotExist:
                try:
                    device = Device.objects.select_related('management', 'company', 'shop', 'borrowed_by').get(
                        device_id__iexact=device_id
                    )
                    print(f"Found device with case-insensitive match: {device.device_id}")
                except Device.DoesNotExist:
                    all_devices = Device.objects.all()[:10]
                    return Response(
                        {
                            "success": False,
                            "error": f"Device '{device_id}' not found",
                            "available_devices": [d.device_id for d in all_devices]
                        },
                        status=status.HTTP_404_NOT_FOUND
                    )

            user_has_access = True
            if hasattr(request.user, 'company') and request.user.company:
                if device.company != request.user.company:
                    user_has_access = False
            elif hasattr(request.user, 'companies'):
                if device.company not in request.user.companies.all():
                    user_has_access = False

            if not user_has_access:
                return Response(
                    {
                        "success": False,
                        "error": "You don't have permission to view this device"
                    },
                    status=status.HTTP_403_FORBIDDEN
                )

            management_info = {}
            if hasattr(device, 'management') and device.management:
                management_info = {
                    "status": device.management.management_status,
                    "block_reason": device.management.block_reason,
                    "blocked_at": device.management.blocked_at,
                    "blocked_by": device.management.blocked_by.username if device.management.blocked_by else None,
                    "last_command": device.management.last_command,
                    "last_command_at": device.management.last_command_at,
                }
            else:
                management_info = {
                    "status": "active",
                    "block_reason": None,
                    "blocked_at": None,
                    "blocked_by": None,
                    "last_command": None,
                    "last_command_at": None,
                }

            return Response({
                "success": True,
                "installation_completed": device.installation_completed,
                "installation_completed_at": device.installation_completed_at,
                "device": {
                    "id": device.device_id,
                    "type": device.device_type,
                    "model": f"{device.manufacturer} {device.model}",
                    "status": device.loan_status,
                    "installation_completed": device.installation_completed,
                    "installation_completed_at": device.installation_completed_at,
                    "is_online": device.is_online,
                    "last_seen": device.last_seen_at,
                    "ip_address": device.ip_address,
                    "is_locked": device.is_locked,
                    "is_trusted": device.is_trusted,
                },
                "management": management_info,
                "assignment": {
                    "borrowed_by": {
                        "id": device.borrowed_by.id,
                        "name": device.borrowed_by.get_full_name() if device.borrowed_by else None,
                        "user_ref": device.borrowed_by.user_ref if device.borrowed_by else None,
                    } if device.borrowed_by else None,
                    "registered_by": device.registered_by.get_full_name() if device.registered_by else None,
                    "shop": device.shop.shop_name if device.shop else None,
                    "company": device.company.name if device.company else None,
                }
            })

        except Exception as e:
            import traceback
            traceback.print_exc()
            return Response({
                "success": False,
                "error": "Internal server error",
                "message": str(e)
            }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


@extend_schema(
    tags=["Device Management"],
    request=DeviceManagementRequestSerializer,
    responses={200: dict}
)
class DeviceManagementView(APIView):
    """Manage device lock/unlock only"""
    permission_classes = [IsAuthenticated]

    def post(self, request, device_id):
        action = request.data.get('action')

        if not action:
            return Response(
                {"error": "Action is required"},
                status=status.HTTP_400_BAD_REQUEST
            )

        try:
            device = Device.objects.select_related('management').get(device_id=device_id)
        except Device.DoesNotExist:
            return Response(
                {"error": "Device not found"},
                status=status.HTTP_404_NOT_FOUND
            )

        if not hasattr(device, 'management'):
            DeviceManagement.objects.create(device=device)
            device.refresh_from_db()

        management = device.management

        if action == 'lock':
            success = management.lock_device(
                locked_by=request.user,
                request=request
            )
            if success:
                return Response({
                    "success": True,
                    "message": "Device locked successfully",
                    "management_status": management.management_status,
                    "is_locked": device.is_locked,
                })
            else:
                return Response({"error": "Device is already locked"}, status=status.HTTP_400_BAD_REQUEST)

        elif action == 'unlock':
            success = management.unlock_device(
                unlocked_by=request.user,
                request=request
            )
            if success:
                return Response({
                    "success": True,
                    "message": "Device unlocked successfully",
                    "management_status": management.management_status,
                    "is_locked": device.is_locked,
                })
            else:
                return Response({"error": "Device is not locked"}, status=status.HTTP_400_BAD_REQUEST)

        else:
            return Response({
                "error": f"Unknown action: {action}. Only 'lock' and 'unlock' are supported for now"
            }, status=status.HTTP_400_BAD_REQUEST)


@extend_schema(
    tags=["Device Management"],
    responses={200: DeviceHistorySerializer(many=True)}
)
class DeviceHistoryView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, device_id):
        try:
            try:
                device = Device.objects.get(device_id=device_id)
            except Device.DoesNotExist:
                try:
                    device = Device.objects.get(device_id__iexact=device_id)
                except Device.DoesNotExist:
                    all_device_ids = list(Device.objects.values_list('device_id', flat=True)[:20])
                    return Response(
                        {
                            "success": False,
                            "error": f"Device '{device_id}' not found",
                            "available_devices": all_device_ids,
                            "total_devices_in_db": Device.objects.count()
                        },
                        status=status.HTTP_404_NOT_FOUND
                    )

            action = request.GET.get("action")
            days = request.GET.get("days", 7)

            try:
                days_int = int(days)
                if days_int < 1 or days_int > 365:
                    days_int = 7
            except (ValueError, TypeError):
                days_int = 7

            from_date = timezone.now() - timezone.timedelta(days=days_int)

            history_qs = device.history.filter(created_at__gte=from_date)
            if action:
                history_qs = history_qs.filter(action=action)

            total_count = history_qs.count()
            history_qs = history_qs.select_related("user").order_by("-created_at")
            serializer = DeviceHistorySerializer(history_qs, many=True)

            return Response({
                "success": True,
                "device_id": device.device_id,
                "device_name": f"{device.manufacturer} {device.model}",
                "total_entries": total_count,
                "history": serializer.data,
                "period_days": days_int,
                "filters_applied": {"action": action, "days": days_int}
            }, status=status.HTTP_200_OK)

        except Exception as e:
            import traceback
            traceback.print_exc()
            return Response({
                "success": False,
                "error": "Internal server error",
                "message": str(e) if settings.DEBUG else "An error occurred"
            }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


# ============================================================
# HEARTBEAT COMPARISON HELPER FUNCTIONS
# ============================================================

# Fields to track for heartbeat comparison
HEARTBEAT_TRACKED_FIELDS = [
    "device_imeis",
    "serial_number",
    "installed_ram",
    "total_storage",
    "is_device_rooted",
    "is_usb_debugging_enabled",
    "is_developer_mode_enabled",
    "is_bootloader_unlocked",
    "is_custom_rom",
]

# HIGH severity fields - auto-lock device (security critical)
HIGH_SEVERITY_FIELDS = {
    "serial_number",
    "device_imeis",
    "is_device_rooted",
    "is_usb_debugging_enabled",
    "is_developer_mode_enabled",
    "is_bootloader_unlocked",
}

# MEDIUM severity fields - log only (hardware/config changes)
MEDIUM_SEVERITY_FIELDS = {
    "installed_ram",
    "total_storage",
    "is_custom_rom",
}


def _get_registration_baseline(device):
    """
    Retrieve the original registration data for a device.
    Returns the new_values from the device creation history entry.
    """
    registration_entry = device.history.filter(action='create').first()
    if registration_entry and registration_entry.new_values:
        return registration_entry.new_values
    return {}


def _get_mismatch_reason(field):
    """
    Generate a generic reason for field mismatch without exposing values.
    """
    reasons = {
        "serial_number": "Device serial number mismatch detected",
        "device_imeis": "Device IMEI mismatch detected",
        "is_device_rooted": "Device rooting status changed",
        "is_usb_debugging_enabled": "USB debugging status changed",
        "is_developer_mode_enabled": "Developer mode status changed",
        "is_bootloader_unlocked": "Bootloader unlock status changed",
        "is_custom_rom": "Custom ROM status changed",
        "installed_ram": "Device RAM configuration changed",
        "total_storage": "Device storage configuration changed",
    }
    return reasons.get(field, f"Field '{field}' value changed")


def _calculate_severity(field):
    """
    Determine severity level based on field type.
    HIGH: serial_number, device_imeis, security flags
    MEDIUM: hardware specs, custom_rom
    """
    if field in HIGH_SEVERITY_FIELDS:
        return "high"
    elif field in MEDIUM_SEVERITY_FIELDS:
        return "medium"
    return "medium"


def _normalize_for_comparison(value):
    """
    Normalize values for comparison to avoid false positives.
    
    Handles:
    - Storage values: "5 GB" → "5gb" (remove spaces, lowercase)
    - IMEI lists: ["123"] → ("123",) (convert to sorted tuple)
    - String case: normalize to lowercase
    - Whitespace: strip leading/trailing spaces
    """
    if value is None:
        return None
    
    # Handle lists (e.g., device_imeis) - convert to sorted tuple for consistent comparison
    if isinstance(value, list):
        normalized_items = []
        for item in value:
            if isinstance(item, str):
                normalized_items.append(item.strip().lower())
            else:
                normalized_items.append(str(item).lower())
        return tuple(sorted(normalized_items))
    
    # Handle strings
    if isinstance(value, str):
        # Remove spaces from storage values (GB, MB, TB, KB)
        if any(unit in value.upper() for unit in ["GB", "MB", "TB", "KB"]):
            return value.replace(" ", "").lower()
        # Normalize other strings
        return value.lower().strip()
    
    # Handle dicts
    if isinstance(value, dict):
        return {k: _normalize_for_comparison(v) for k, v in value.items()}
    
    # Return other types as-is (bool, int, etc.)
    return value


def _compare_imei_lists(registered_imeis, current_imeis):
    """
    Compare IMEI lists with flexible matching and security warnings.
    
    Logic:
    1. Kama device inatuma IMEI 2 → Angalia zote 2 ziko kwenye baseline
    2. Kama device inatuma IMEI 1 → Angalia hiyo 1 iko kwenye baseline
    3. Kama IMEI mpya imeongezwa (haiko baseline) → MISMATCH!
    4. Kama IMEI imeondolewa (ilikuwa baseline lakini sasa haipo) → OK lakini LOG WARNING
    
    Examples:
    - Baseline: [A, B], Current: [A, B] → (True, None) - OK (zote ziko)
    - Baseline: [A, B], Current: [A] → (True, "WARNING: IMEI count decreased...") - OK lakini warning
    - Baseline: [A, B], Current: [B] → (True, "WARNING: IMEI count decreased...") - OK lakini warning
    - Baseline: [A, B], Current: [A, C] → (False, None) - MISMATCH (C ni mpya!)
    - Baseline: [A], Current: [A, B] → (False, None) - MISMATCH (B ni mpya!)
    - Baseline: [A, B], Current: [C] → (False, None) - MISMATCH (C ni mpya!)
    - Baseline: [A], Current: [A] → (True, None) - OK (same)
    - Baseline: [A], Current: [B] → (False, None) - MISMATCH (changed)
    
    Returns:
        tuple: (is_ok: bool, warning_message: str or None)
        - is_ok: True if all current IMEIs are in baseline, False if new IMEI detected
        - warning_message: Warning text if IMEI count decreased (possible SIM swap hiding), None otherwise
    """
    if not registered_imeis or not current_imeis:
        return (True, None)  # Skip if either is empty
    
    # Normalize to lowercase for comparison
    registered_normalized = [str(imei).strip().lower() for imei in registered_imeis]
    current_normalized = [str(imei).strip().lower() for imei in current_imeis]
    
    # Check if ALL current IMEIs are in the baseline
    # This allows:
    # - Sending fewer IMEIs (SIM removed) ✅
    # - Sending same IMEIs ✅
    # But blocks:
    # - Sending new IMEIs not in baseline ❌
    for current_imei in current_normalized:
        if current_imei not in registered_normalized:
            # Found an IMEI that's NOT in baseline → MISMATCH!
            return (False, None)
    
    # All current IMEIs are in baseline → OK
    # But check if IMEI count decreased (security warning)
    warning_message = None
    if len(current_normalized) < len(registered_normalized):
        # IMEI count decreased - could be legitimate SIM removal OR hiding SIM swap
        missing_count = len(registered_normalized) - len(current_normalized)
        missing_imeis = [imei for imei in registered_normalized if imei not in current_normalized]
        
        warning_message = (
            f"⚠️ SECURITY WARNING: IMEI count decreased from {len(registered_normalized)} to {len(current_normalized)}. "
            f"Missing {missing_count} IMEI(s). This could be legitimate SIM removal OR an attempt to hide SIM swap. "
            f"Baseline had {len(registered_normalized)} IMEI(s), now reporting {len(current_normalized)}. "
            f"Manual review recommended."
        )
    
    return (True, warning_message)


def _compare_ram_with_tolerance(registered_ram, current_ram):
    """
    Compare RAM values with -1GB tolerance for desktop/laptop devices.
    
    Logic:
    - Extract numeric value from RAM string (e.g., "16 GB" → 16)
    - Allow current RAM to be up to 1GB less than registered (tolerance for OS overhead)
    - If current RAM < (registered - 1GB) → MISMATCH (possible hardware swap)
    
    Examples:
    - Registered: "16 GB", Current: "16 GB" → OK
    - Registered: "16 GB", Current: "15.79 GB" → OK (within tolerance)
    - Registered: "16 GB", Current: "15 GB" → OK (exactly at threshold)
    - Registered: "16 GB", Current: "14.9 GB" → MISMATCH (below threshold)
    - Registered: "8 GB", Current: "7.5 GB" → OK
    - Registered: "8 GB", Current: "6.9 GB" → MISMATCH
    
    Returns:
        tuple: (is_ok: bool, reason: str or None)
    """
    import re
    
    if not registered_ram or not current_ram:
        return (True, None)
    
    # Extract numeric value from RAM string
    def extract_ram_gb(ram_str):
        """Extract RAM value in GB from string like '16 GB' or '15.79 GB'"""
        if not isinstance(ram_str, str):
            return None
        
        # Match patterns like "16 GB", "15.79 GB", "16GB", etc.
        match = re.search(r'(\d+\.?\d*)\s*GB', ram_str, re.IGNORECASE)
        if match:
            return float(match.group(1))
        return None
    
    registered_gb = extract_ram_gb(registered_ram)
    current_gb = extract_ram_gb(current_ram)
    
    if registered_gb is None or current_gb is None:
        # Can't parse RAM values - skip comparison
        return (True, None)
    
    # Calculate minimum allowed RAM (registered - 1GB)
    min_allowed_gb = registered_gb - 1.0
    
    if current_gb < min_allowed_gb:
        # RAM is below threshold - possible hardware swap
        reason = f"RAM decreased significantly: registered {registered_gb}GB, current {current_gb}GB (minimum allowed: {min_allowed_gb}GB)"
        return (False, reason)
    
    return (True, None)


def _compare_heartbeat_with_registration(device, incoming_data):
    """
    Compare incoming heartbeat data with registration baseline.


    
    FLOW:
    1. Retrieve registration baseline from device history
    2. Compare ONLY tracked fields
    3. Normalize values before comparison (avoid false positives)
    4. Classify severity (HIGH or MEDIUM)
    5. Generate reasons (no data values exposed)
    6. Determine if auto-lock needed (HIGH severity)
    
    Returns:
    - mismatches: List of detected changes with field, severity, reason
    - should_auto_lock: True if HIGH severity mismatches found
    - lock_reason: Reason for auto-lock
    - comparison_result: Full comparison data for history storage
    """
    registration_data = _get_registration_baseline(device)
    
    # CRITICAL FIX: If registration baseline is empty, this is likely the first heartbeat
    # after registration. Skip comparison to avoid false positives.
    # The baseline should have been created during device registration.
    # Check if baseline exists AND has at least one non-None value
    if not registration_data:
        # No baseline found at all - skip comparison
        return {
            "mismatches": [],
            "high_severity_count": 0,
            "medium_severity_count": 0,
            "total_mismatches": 0,
            "should_auto_lock": False,
            "lock_reason": None,
            "comparison_details": {},
            "baseline_status": "not_established"
        }
    
    # Check if baseline has any actual data (not just empty dict)
    has_baseline_data = any(
        v is not None and v != "" and v != [] 
        for v in registration_data.values()
    )
    
    if not has_baseline_data:
        # Baseline exists but is empty - skip comparison
        return {
            "mismatches": [],
            "high_severity_count": 0,
            "medium_severity_count": 0,
            "total_mismatches": 0,
            "should_auto_lock": False,
            "lock_reason": None,
            "comparison_details": {},
            "baseline_status": "empty_baseline"
        }
    
    mismatches = []
    high_severity_count = 0
    medium_severity_count = 0
    comparison_details = {}
    
    # Define mobile-only fields that should be skipped for desktop/laptop devices
    MOBILE_ONLY_FIELDS = {
        'device_imeis',
        'is_device_rooted',
        'is_usb_debugging_enabled',
        'is_developer_mode_enabled',
        'is_bootloader_unlocked',
    }
    
    # Define fields to skip for desktop/laptop devices (not needed for comparison)
    DESKTOP_SKIP_FIELDS = {
        'serial_number',
        'total_storage',
        'is_custom_rom',
    }
    
    for field in HEARTBEAT_TRACKED_FIELDS:
        if field not in incoming_data:
            continue
        
        # Skip mobile-specific fields for desktop/laptop devices
        if device.device_type in ['desktop', 'laptop'] and field in MOBILE_ONLY_FIELDS:
            continue
        
        # Skip unnecessary fields for desktop/laptop devices
        if device.device_type in ['desktop', 'laptop'] and field in DESKTOP_SKIP_FIELDS:
            continue
        
        registered = registration_data.get(field)
        current = incoming_data.get(field)
        
        # Special handling for IMEI comparison (allow subset matching)
        if field == "device_imeis":
            is_ok, warning_message = _compare_imei_lists(registered, current)
            
            if not is_ok:
                # MISMATCH detected - new IMEI found
                severity = _calculate_severity(field)
                reason = _get_mismatch_reason(field)
                
                mismatches.append({
                    "field": field,
                    "severity": severity,
                    "reason": reason
                })
                
                comparison_details[field] = {
                    "status": "mismatch",
                    "severity": severity,
                    "registered": registered,
                    "current": current,
                    "reason": reason
                }
                
                if severity == "high":
                    high_severity_count += 1
                else:
                    medium_severity_count += 1
            else:
                # OK - but check for warning (IMEI count decreased)
                comparison_details[field] = {
                    "status": "matched",
                    "registered": registered,
                    "current": current
                }
                
                # If there's a warning, add it to comparison details
                if warning_message:
                    comparison_details[field]["warning"] = warning_message
                    comparison_details[field]["status"] = "matched_with_warning"
            continue
        
        # Special handling for RAM comparison (desktop/laptop only - with -1GB tolerance)
        if field == "installed_ram" and device.device_type in ['desktop', 'laptop']:
            is_ok, ram_reason = _compare_ram_with_tolerance(registered, current)
            
            if not is_ok:
                # RAM MISMATCH detected - below threshold
                severity = "high"  # HIGH severity for desktop RAM changes
                reason = ram_reason or "Device RAM significantly decreased"
                
                mismatches.append({
                    "field": field,
                    "severity": severity,
                    "reason": reason
                })
                
                comparison_details[field] = {
                    "status": "mismatch",
                    "severity": severity,
                    "registered": registered,
                    "current": current,
                    "reason": reason
                }
                
                high_severity_count += 1
            else:
                # RAM OK - within tolerance
                comparison_details[field] = {
                    "status": "matched",
                    "registered": registered,
                    "current": current
                }
            continue
        
        # Normalize both values before comparison
        registered_normalized = _normalize_for_comparison(registered)
        current_normalized = _normalize_for_comparison(current)
        
        # Skip if values are the same after normalization
        if registered_normalized == current_normalized:
            comparison_details[field] = {
                "status": "matched",
                "registered": registered,
                "current": current
            }
            continue
        
        # Skip if both are None or empty
        if not registered_normalized and not current_normalized:
            comparison_details[field] = {
                "status": "both_empty",
                "registered": registered,
                "current": current
            }
            continue
        
        severity = _calculate_severity(field)
        reason = _get_mismatch_reason(field)
        
        mismatches.append({
            "field": field,
            "severity": severity,
            "reason": reason
        })
        
        comparison_details[field] = {
            "status": "mismatch",
            "severity": severity,
            "registered": registered,
            "current": current,
            "reason": reason
        }
        
        if severity == "high":
            high_severity_count += 1
        else:
            medium_severity_count += 1
    
    # Determine if device should be auto-locked
    should_auto_lock = high_severity_count > 0
    lock_reason = None
    
    if should_auto_lock:
        high_fields = [m["field"] for m in mismatches if m["severity"] == "high"]
        lock_reason = f"Device security compromised: {', '.join(high_fields)}"
    
    return {
        "mismatches": mismatches,
        "high_severity_count": high_severity_count,
        "medium_severity_count": medium_severity_count,
        "total_mismatches": len(mismatches),
        "should_auto_lock": should_auto_lock,
        "lock_reason": lock_reason,
        "comparison_details": comparison_details
    }


def _get_next_payment_info(device):
    """
    Get next payment information for a device.
    
    Returns:
    {
        "date_time": "2026-02-07T23:59:00+03:00",
        "unlock_password": "ABC123"
    }
    or None if no active/approved loan or no pending payments
    """
    from loans.models import Loan, LoanPayment
    from datetime import datetime, time as dt_time
    
    try:
        # Get active OR approved loan for this device
        loan = Loan.objects.select_related("device").filter(
            device=device,
            status__in=['approved', 'active']  # Support both statuses
        ).first()
        
        if not loan:
            return None
        
        # Get next unpaid installment
        next_installment = LoanPayment.objects.filter(
            loan=loan,
            status__in=['pending', 'partial', 'overdue']
        ).order_by('due_date', 'installment_number').first()
        
        if not next_installment or not next_installment.due_date:
            return None
        
        # Calculate date_time (end of day)
        dt = datetime.combine(next_installment.due_date, dt_time(23, 59, 0))
        try:
            dt = timezone.make_aware(dt, timezone.get_current_timezone())
        except Exception:
            pass
        try:
            dt = timezone.localtime(dt)
        except Exception:
            pass
        
        # Get or generate unlock password
        unlock_password = getattr(device, 'pending_unlock_password', None)
        if not unlock_password:
            # Generate temporary password without saving to database
            import string
            import secrets
            alphabet = string.ascii_letters + string.digits
            unlock_password = ''.join(secrets.choice(alphabet) for i in range(12))
        
        return {
            "date_time": dt.isoformat(),
            "unlock_password": unlock_password
        }
        
    except Exception as e:
        import logging
        logger = logging.getLogger(__name__)
        logger.error(f"Error getting next payment info for device {device.device_id}: {str(e)}")
        return None


def _auto_request_deactivation_for_completed_loan(device):
    """
    Auto-request deactivation when the device has no open loan and at least one completed loan.

    Returns:
    {
        "should_deactivate": bool,
        "reason": "loan_completed" | None,
        "loan_number": str | None,
    }
    """
    try:
        open_statuses = ["draft", "pending", "approved", "active"]
        if Loan.objects.filter(device=device, status__in=open_statuses).exists():
            return {"should_deactivate": False, "reason": None, "loan_number": None}

        completed_loan = (
            Loan.objects.filter(device=device, status="completed")
            .order_by("-actual_end_date", "-updated_at", "-created_at")
            .first()
        )
        if not completed_loan:
            return {"should_deactivate": False, "reason": None, "loan_number": None}

        if not device.deactivated_at and not device.deactivate_requested:
            device.deactivate_requested = True
            device.save(update_fields=["deactivate_requested"])
            device.create_history_entry(
                action="deactivation_requested",
                notes=(
                    "Device Owner deactivation auto-requested after loan completion "
                    f"({completed_loan.loan_number})."
                ),
                changed_fields=["deactivate_requested"],
                old_values={"deactivate_requested": False},
                new_values={"deactivate_requested": True, "trigger": "loan_completed"},
            )

        return {
            "should_deactivate": True,
            "reason": "loan_completed",
            "loan_number": completed_loan.loan_number,
        }
    except Exception as e:
        logger.warning(
            "Auto deactivation check failed for device %s: %s",
            getattr(device, "device_id", "unknown"),
            str(e),
        )
        return {"should_deactivate": False, "reason": None, "loan_number": None}


def _build_device_response(device, comparison_result, heartbeat_history, deactivation_meta=None):
    """
    Build simple standardized device response for heartbeat endpoint.
    
    Response format:
    {
        "success": true/false,
        "message": "...",
        "content": {
            "is_locked": true/false,
            "reason": "Payment overdue" | "Security issue" (only if locked)
        },
        "server_time": "2026-02-05T14:30:00+03:00",
        "next_payment": {
            "date_time": "2026-02-07T23:59:00+03:00",
            "unlock_password": "ABC123"
        },
        "deactivation": {
            "status": "requested" | "none",
            "command": "DEACTIVATE_NOW" (only if requested),
            "reason": "loan_completed" (optional),
            "agent_notice": "Time to remove the device agent." (optional)
        }
    }
    """
    from loans.models import Loan, LoanPayment
    
    high_count = comparison_result.get("high_severity_count", 0)
    
    # Get server time
    server_time = timezone.localtime(timezone.now()).isoformat()
    
    # Get next payment info
    next_payment = _get_next_payment_info(device)
    
    # Check deactivation status (manual request OR auto after loan completion)
    deactivation_meta = deactivation_meta or {}
    deactivation_requested = (
        getattr(device, 'deactivate_requested', False)
        or bool(deactivation_meta.get("should_deactivate"))
    )
    deactivation = {
        "status": "requested" if deactivation_requested else "none"
    }
    if deactivation_requested:
        deactivation["command"] = "DEACTIVATE_NOW"
        if deactivation_meta.get("reason") == "loan_completed":
            deactivation["reason"] = "loan_completed"
            deactivation["agent_notice"] = "Time to remove the device agent."
            if deactivation_meta.get("loan_number"):
                deactivation["loan_number"] = deactivation_meta.get("loan_number")
    
    # Check if device is locked
    if device.is_locked:
        # Device is locked - determine reason
        
        # Check for payment overdue (PRIORITY CHECK)
        today = timezone.now().date()
        loan = Loan.objects.filter(device=device, status='active').first()
        has_overdue = False
        
        if loan:
            overdue_installments = LoanPayment.objects.filter(
                loan=loan,
                due_date__lt=today,
                status__in=['pending', 'partial', 'overdue']
            ).exists()
            has_overdue = overdue_installments
        
        if has_overdue:
            # PAYMENT OVERDUE
            content = {
                "is_locked": True,
                "reason": "Payment overdue"
            }
        elif high_count > 0:
            # SECURITY VIOLATION
            content = {
                "is_locked": True,
                "reason": "Security issue"
            }
        else:
            # LOCKED but no clear reason - default to payment
            content = {
                "is_locked": True,
                "reason": "Payment overdue"
            }
    else:
        # Device is UNLOCKED
        content = {
            "is_locked": False
        }
    
    # Build response
    response = {
        "success": True,
        "message": "Heartbeat processed successfully",
        "content": content,
        "server_time": server_time,
        "deactivation": deactivation
    }
    
    if next_payment:
        response["next_payment"] = next_payment
    
    return response


@extend_schema(
    tags=["Device Data"],
    request=DeviceDataPostSerializer,
    responses={
        200: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device data processed successfully"),
        400: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Invalid data"),
        404: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Device not found")
    },
)
class DeviceDataPostView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    @extend_schema(
        tags=["Devices"],
        description="Device heartbeat/data post (requires device agent API key).",
        parameters=[DEVICE_API_HEADER_PARAM],
        request=DeviceDataPostSerializer,
        responses={
            200: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Heartbeat processed"),
            401: OpenApiResponse(description="Missing API key"),
            403: OpenApiResponse(description="Invalid API key"),
        },
        examples=[],
    )
    def post(self, request, device_id):
        deny = _require_device_api_key(request)
        if deny:
            return deny

        """
        Process device heartbeat with comparison against registration baseline.
        
        FLOW:
        1. Validate incoming heartbeat data
        2. Store heartbeat data in DeviceHeartbeatHistory (immutable snapshot)
        3. Store heartbeat in DeviceHistory (audit trail)
        4. Compare heartbeat vs registration baseline
        5. Classify mismatches by severity (HIGH or MEDIUM)
        6. Auto-lock device if HIGH severity mismatches detected
        7. Return response with recommendations only (NO data values exposed)
        
        RESPONSE STRUCTURE:
        {
            "success": true/false,
            "message": "Status message",
            "device": {
                "device_id": "...",
                "status": "locked" or "active",
                "is_online": true
            },
            "management": {
                "status": "locked" or "active",
                "is_locked": true/false,
                "reason": "Lock reason if locked"
            },
            "mismatches": [
                {
                    "field": "field_name",
                    "severity": "high" or "medium",
                    "reason": "Generic reason without data values"
                }
            ],
            "recommendations": [
                {
                    "type": "security_alert" or "info",
                    "action": "LOCK_DEVICE" or "LOG_ONLY",
                    "message": "Actionable message"
                }
            ]
        }
        """
        try:
            logger.info("Heartbeat received for device_id=%s (Content-Type=%s)", device_id, request.content_type)
            # Validate incoming data
            serializer = DeviceDataPostSerializer(data=request.data)
            serializer.is_valid(raise_exception=True)

            # Get device
            device = get_object_or_404(Device, device_id=device_id)
            
            # Normalize heartbeat data
            incoming_data, extra_data = _normalize_mobile_heartbeat(serializer.validated_data)

            # Update device status fields only
            device.is_online = True
            device.last_online_at = timezone.now()
            device.last_seen_at = timezone.now()
            device.ip_address = request.META.get("REMOTE_ADDR")
            device.save(update_fields=["is_online", "last_online_at", "last_seen_at", "ip_address"])

            # ============================================================
            # COMPARE HEARTBEAT WITH REGISTRATION BASELINE
            # ============================================================
            comparison_result = _compare_heartbeat_with_registration(device, incoming_data)
            
            mismatches = comparison_result["mismatches"]
            high_severity_count = comparison_result["high_severity_count"]
            medium_severity_count = comparison_result["medium_severity_count"]
            total_mismatches = comparison_result["total_mismatches"]
            should_auto_lock = comparison_result["should_auto_lock"]
            lock_reason = comparison_result["lock_reason"]
            comparison_details = comparison_result["comparison_details"]

            # ============================================================
            # STORE HEARTBEAT IN IMMUTABLE HISTORY (DeviceHeartbeatHistory)
            # ============================================================
            heartbeat_history = DeviceHeartbeatHistory.objects.create(
                device=device,
                heartbeat_data=incoming_data,
                comparison_result={
                    "mismatches": mismatches,
                    "comparison_details": comparison_details,
                    "high_severity_count": high_severity_count,
                    "medium_severity_count": medium_severity_count,
                    "total_mismatches": total_mismatches
                },
                mismatches_detected=total_mismatches > 0,
                high_severity_count=high_severity_count,
                medium_severity_count=medium_severity_count,
                ip_address=request.META.get("REMOTE_ADDR"),
                user_agent=request.META.get("HTTP_USER_AGENT")
            )

            # ============================================================
            # STORE HEARTBEAT IN AUDIT HISTORY (DeviceHistory)
            # ============================================================
            history_notes = {
                "heartbeat_timestamp": timezone.now().isoformat(),
                "incoming_data": incoming_data,
                "extra_data": extra_data,
                "comparison_result": {
                    "mismatches": mismatches,
                    "high_severity_count": high_severity_count,
                    "medium_severity_count": medium_severity_count,
                    "total_mismatches": total_mismatches
                },
                "heartbeat_history_id": heartbeat_history.id
            }

            from django.core.serializers.json import DjangoJSONEncoder
            device.create_history_entry(
                action="heartbeat_mismatch" if total_mismatches > 0 else "heartbeat",
                request=request,
                notes=json.dumps(history_notes, cls=DjangoJSONEncoder)
            )

            # ============================================================
            # AUTO-LOCK DEVICE IF HIGH SEVERITY MISMATCHES DETECTED
            # ============================================================
            if should_auto_lock and not device.is_locked:
                try:
                    # Ensure device has management record
                    if not hasattr(device, 'management') or not device.management:
                        DeviceManagement.objects.create(device=device)
                        device.refresh_from_db()
                    
                    # Lock device
                    device.management.block_reason = lock_reason
                    device.management.blocked_at = timezone.now()
                    device.management.save(update_fields=['block_reason', 'blocked_at'])
                    device.management.lock_device(locked_by=None, request=request)
                    
                    # Update heartbeat history with lock info
                    heartbeat_history.auto_locked = True
                    heartbeat_history.lock_reason = lock_reason
                    heartbeat_history.save(update_fields=['auto_locked', 'lock_reason'])
                    
                    # Create temper signal
                    DeviceTemperSignal.objects.create(
                        device=device,
                        signal_type="device_tampered",
                        level="high",
                        description=lock_reason,
                        auto_action_taken=True,
                        action_description="Device automatically locked due to security violations"
                    )
                except Exception as e:
                    print(f"Error auto-locking device: {str(e)}")

            # ============================================================
            # CREATE SECURITY ALERT IF MISMATCHES DETECTED
            # ============================================================
            if total_mismatches > 0:
                alert_level = "high" if high_severity_count > 0 else "medium"
                mismatch_fields = [m["field"] for m in mismatches]
                
                DeviceTemperSignal.objects.create(
                    device=device,
                    signal_type="security_breach" if high_severity_count > 0 else "system_alert",
                    level=alert_level,
                    description=f"Device data mismatch detected: {', '.join(mismatch_fields)}"
                )

            # ============================================================
            # SERVER-SIDE LOGGING FOR DEBUGGING
            # ============================================================
            import logging
            logger = logging.getLogger(__name__)
            
            if total_mismatches > 0:
                logger.info(f"Device {device_id} mismatches: {total_mismatches} "
                           f"(High: {high_severity_count}, Medium: {medium_severity_count})")
                logger.info(f"Mismatch fields: {[m['field'] for m in mismatches]}")
                
            if should_auto_lock:
                logger.warning(f"Device {device_id} auto-locked due to security violations: {lock_reason}")
            
            # Check for IMEI warnings (count decreased - possible SIM swap hiding)
            if 'device_imeis' in comparison_details:
                imei_details = comparison_details['device_imeis']
                if imei_details.get('status') == 'matched_with_warning':
                    warning_msg = imei_details.get('warning', '')
                    logger.warning(f"Device {device_id} IMEI WARNING: {warning_msg}")
                    logger.warning(f"Device {device_id} - Baseline IMEIs: {imei_details.get('registered')}, "
                                 f"Current IMEIs: {imei_details.get('current')}")


            # ============================================================
            # BUILD RECOMMENDATIONS FOR CUSTOMER SUPPORT
            # ============================================================
            recommendations = []
            
            if high_severity_count > 0:
                shop_name = device.shop.shop_name if device.shop else "the shop where you purchased"
                recommendations.append({
                    "type": "security_alert",
                    "action": "VISIT_SHOP",
                    "message": f"Device has been locked due to security violations. Please visit {shop_name} for assistance."
                })
            
            if medium_severity_count > 0:
                recommendations.append({
                    "type": "info",
                    "action": "LOG_ONLY",
                    "message": "Configuration changes detected and logged for audit trail."
                })

            if total_mismatches == 0:
                recommendations.append({
                    "type": "success",
                    "action": "CONTINUE",
                    "message": "Device data matches registration baseline. No issues detected."
                })

            # ============================================================
            # ENSURE DEVICE HAS MANAGEMENT RECORD
            # ============================================================
            if not hasattr(device, 'management') or not device.management:
                DeviceManagement.objects.create(device=device)
                device.refresh_from_db()

            # ============================================================
            # BUILD SIMPLIFIED RESPONSE (success, message, content ONLY)
            # ============================================================
            # Build response using the standardized format - NO extra data
            deactivation_meta = _auto_request_deactivation_for_completed_loan(device)
            response_data = _build_device_response(
                device,
                comparison_result,
                heartbeat_history,
                deactivation_meta=deactivation_meta,
            )
            
            return Response(response_data, status=status.HTTP_200_OK)

        except Device.DoesNotExist:
            return Response(
                {
                    "success": False,
                    "error": f"Device '{device_id}' not found",
                    "message": "Please ensure the device is registered before sending heartbeats"
                },
                status=status.HTTP_404_NOT_FOUND
            )
        except Exception as e:
            import traceback
            traceback.print_exc()
            logger.exception("Heartbeat 500 for device_id=%s: %s", device_id, e)
            try:
                err_message = str(e) if settings.DEBUG else "An error occurred processing the heartbeat"
            except Exception:
                err_message = "An error occurred processing the heartbeat"
            return Response(
                {
                    "success": False,
                    "error": "Internal server error",
                    "message": err_message,
                },
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


class GetDeviceBiosPasswordView(APIView):
    permission_classes = [AllowAny]

    @extend_schema(
        tags=["Devices"],
        description="Get BIOS password for a device (requires device agent API key).",
        parameters=[DEVICE_API_HEADER_PARAM],
        responses={200: dict},
        examples=[],
    )
    def get(self, request, device_id):
        deny = _require_device_api_key(request)
        if deny:
            return deny

        device = get_object_or_404(Device, device_id=device_id)
        if not device.pending_bios_password:
            device.generate_bios_password()

        return Response({
            "success": True,
            "device_id": device.device_id,
            "bios_password": device.pending_bios_password,
            "instruction": "SET_BIOS_SECURITY"
        }, status=status.HTTP_200_OK)


@extend_schema(
    tags=["Device Management"],
    request=DeviceListSerializer,
    responses={200: dict}
)
class DeviceListView(ListAPIView):
    queryset = Device.objects.select_related(
        "shop",
        "company",
        "registered_by",
        "borrowed_by",
        "management",
        "device_category",
    ).all()
    serializer_class = DeviceListSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        qs = super().get_queryset()

        if user.is_superuser:
            scoped = qs
        elif getattr(user, "is_company_admin", False) and getattr(user, "company_id", None):
            scoped = qs.filter(company=user.company)
        elif getattr(user, "is_shop_owner", False):
            from shops.models import Shop
            user_shop_ids = Shop.objects.filter(user=user).values_list("id", flat=True)
            scoped = qs.filter(shop_id__in=user_shop_ids)
        elif getattr(user, "company_id", None):
            scoped = qs.filter(company=user.company)
        else:
            scoped = qs.none()

        category = self.request.query_params.get("category")
        if category:
            scoped = scoped.filter(device_category__name=category)
        return scoped


@extend_schema(
    responses=DeviceLoanPaymentSerializer(many=True),
    tags=['Device Installments'],
    description="Get all installments for a specific device",
    parameters=[
        OpenApiParameter(
            name='device_id',
            type=OpenApiTypes.STR,
            location=OpenApiParameter.PATH,
            description='Device ID (string identifier)'
        )
    ],
)
class GetDeviceInstallmentsView(APIView):
    permission_classes = [AllowAny]

    def get(self, request, device_id):
        try:
            device = Device.objects.get(device_id=device_id)
        except Device.DoesNotExist:
            return Response({
                "success": False,
                "error": f"Device not found: {device_id}"
            }, status=status.HTTP_404_NOT_FOUND)

        current_loan = device.loans.filter(
            status__in=['active', 'approved', 'pending']
        ).order_by('-created_at').first()

        if not current_loan:
            return Response({
                "success": True,
                "device_id": device.device_id,
                "installments": []
            })

        installments = current_loan.installments.all().order_by('installment_number')
        serializer = DeviceLoanPaymentSerializer(installments, many=True)

        return Response({
            "success": True,
            "device_id": device.device_id,
            "installments": serializer.data
        })


@extend_schema(
    tags=["Device Security"],
    request=DeviceRecoveryKeySerializer,
    responses={
        200: OpenApiResponse(description="Recovery key saved successfully"),
        404: OpenApiResponse(description="Device not found"),
        400: OpenApiResponse(description="Invalid key format")
    }
)
class DeviceRecoveryKeyView(APIView):
    permission_classes = [AllowAny]

    @extend_schema(
        tags=["Devices"],
        description="Post BitLocker recovery key (requires device agent API key).",
        parameters=[DEVICE_API_HEADER_PARAM],
        request=DeviceRecoveryKeySerializer,
        responses={
            200: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Key saved"),
            401: OpenApiResponse(description="Missing API key"),
            403: OpenApiResponse(description="Invalid API key"),
        },
        examples=[
            OpenApiExample(
                "Payload",
                value={"disk_recovery_key": "123456-123456-123456-123456-123456-123456-123456-123456"},
                request_only=True,
            ),
        ],
    )
    def post(self, request, device_id):
        deny = _require_device_api_key(request)
        if deny:
            return deny

        serializer = DeviceRecoveryKeySerializer(data=request.data)
        if not serializer.is_valid():
            return Response({
                "success": False,
                "error": "Invalid data",
                "details": serializer.errors
            }, status=status.HTTP_400_BAD_REQUEST)

        device = get_object_or_404(Device, device_id=device_id)
        new_key = serializer.validated_data["disk_recovery_key"]

        if device.disk_recovery_key != new_key:
            # Mask old key (handle both dict and string formats for backward compatibility)
            if isinstance(device.disk_recovery_key, dict):
                old_key_masked = f"{{...}} ({len(device.disk_recovery_key)} partitions)"
            elif device.disk_recovery_key:
                old_key_masked = f"{str(device.disk_recovery_key)[:20]}..."
            else:
                old_key_masked = None
            
            device.disk_recovery_key = new_key
            device.save(update_fields=["disk_recovery_key", "last_seen_at"])

            device.create_history_entry(
                action="info_update",
                notes="Disk Recovery Key updated by device agent (Dedicated Endpoint).",
                changed_fields=["disk_recovery_key"],
                old_values={"disk_recovery_key": old_key_masked},
                new_values={"disk_recovery_key": "REDACTED (Saved)"},
                request=request
            )

            if device.loan_status == 'loaned' and old_key_masked:
                DeviceTemperSignal.objects.create(
                    device=device,
                    signal_type="security_alert",
                    level="medium",
                    description="Disk Recovery Key was changed on a loaned device via dedicated endpoint."
                )

        return Response({
            "success": True,
            "message": "Recovery key secured.",
            "device_id": device.device_id
        }, status=status.HTTP_200_OK)


@extend_schema(
    tags=["Devices"],
    description="Check if loan registration is complete and device installation can proceed.",
    responses={200: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Install readiness response")},
)
class DeviceInstallReadyView(APIView):
    permission_classes = [AllowAny]

    @extend_schema(
        tags=["Devices"],
        description="Check if loan registration is complete and installation can continue.",
        parameters=[DEVICE_API_HEADER_PARAM],
        responses={200: OpenApiResponse(response=OpenApiTypes.OBJECT, description="Install readiness response")},
        examples=[
            OpenApiExample(
                "Ready (success)",
                value={
                    "ready": True,
                    "loan_number": "LN-20260131-00001",
                    "message": "ok_ready_install",
                },
                response_only=True,
            ),
            OpenApiExample(
                "Not ready (needs action)",
                value={
                    "ready": False,
                    "loan_number": "LN-20260131-00001",
                    "message": "initial_payment_not_paid",
                },
                response_only=True,
            ),
        ],
    )
    def get(self, request, loan_number):
        deny = _require_device_api_key(request)
        if deny:
            return deny

        loan = get_object_or_404(Loan, loan_number=loan_number)

        reasons = []
        if (loan.initial_payment_status or "").lower() != "paid":
            reasons.append("initial_payment_not_paid")
        if not loan.user_id:
            reasons.append("missing_customer")
        if not loan.shop_id:
            reasons.append("missing_shop")
        if not loan.company_id:
            reasons.append("missing_company")
        if not loan.device_category_id:
            reasons.append("missing_device_category")

        ready = len(reasons) == 0
        message = "ok_ready_install" if ready else (reasons[0] if reasons else "example_finish_fail")

        return Response(
            {
                "ready": ready,
                "loan_number": loan.loan_number,
                "message": message,
            },
            status=status.HTTP_200_OK,
        )


# ============================================================
# DEVICE OWNER DEACTIVATION ENDPOINTS
# ============================================================

@extend_schema(
    tags=["Device Management"],
    summary="Request Device Owner deactivation",
    description="Admin endpoint to request Device Owner deactivation on a device. The device will receive the command on next heartbeat.",
    request=DeviceDeactivationRequestSerializer,
    responses={
        200: DeviceDeactivationResponseSerializer,
        404: OpenApiResponse(description="Device not found"),
        403: OpenApiResponse(description="Permission denied")
    },
    examples=[
        OpenApiExample(
            "Deactivation request",
            value={"reason": "Customer requested device return"},
            request_only=True
        ),
        OpenApiExample(
            "Success response",
            value={
                "success": True,
                "message": "Device Owner deactivation requested successfully",
                "device_id": "ANDROID-123",
                "deactivate_requested": True,
                "requested_at": "2026-02-03T10:30:00Z"
            },
            response_only=True
        )
    ]
)
@extend_schema(
    tags=["Device Management"],
    summary="Request Device Owner deactivation",
    description="Admin endpoint to request Device Owner deactivation. Device will receive command on next heartbeat.",
    request=DeviceDeactivationRequestSerializer,
    responses={
        200: DeviceDeactivationResponseSerializer,
        403: OpenApiResponse(description="Permission denied"),
        404: OpenApiResponse(description="Device not found")
    },
    examples=[
        OpenApiExample(
            "Request deactivation",
            value={
                "reason": "Device returned by customer"
            },
            request_only=True
        ),
        OpenApiExample(
            "Success response",
            value={
                "success": True,
                "message": "Device Owner deactivation requested successfully...",
                "device_id": "ANDROID-123",
                "deactivate_requested": True,
                "requested_at": "2026-02-03T10:00:00Z"
            },
            response_only=True
        )
    ]
)
class DeviceDeactivationRequestView(APIView):
    """Admin endpoint to request Device Owner deactivation"""
    permission_classes = [IsAuthenticated]
    
    def post(self, request, device_id):
        # Step 1: Check admin permissions
        if not (request.user.is_superuser or getattr(request.user, 'is_company_admin', False)):
            logger.warning(f"Non-admin user {request.user.username} attempted to deactivate device {device_id}")
            return Response({
                "success": False,
                "error": "Permission denied. Only admins can deactivate devices."
            }, status=status.HTTP_403_FORBIDDEN)
        
        # Step 2: Get device
        try:
            device = Device.objects.get(device_id=device_id)
        except Device.DoesNotExist:
            logger.warning(f"Deactivation requested for non-existent device: {device_id}")
            return Response({
                "success": False,
                "error": f"Device '{device_id}' not found"
            }, status=status.HTTP_404_NOT_FOUND)
        
        # Step 3: Check company access
        if not request.user.is_superuser:
            if not hasattr(request.user, 'company') or device.company != request.user.company:
                logger.warning(f"User {request.user.username} attempted to deactivate device from different company")
                return Response({
                    "success": False,
                    "error": "You don't have permission to manage this device"
                }, status=status.HTTP_403_FORBIDDEN)
        
        # Step 4: Validate request data
        serializer = DeviceDeactivationRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        
        # Step 5: Check if already deactivated
        if device.deactivated_at is not None:
            logger.info(f"Device {device_id} already deactivated at {device.deactivated_at}")
            return Response({
                "success": False,
                "error": "Device is already deactivated",
                "deactivated_at": device.deactivated_at
            }, status=status.HTTP_400_BAD_REQUEST)
        
        # Step 6: Set deactivation request flag
        device.deactivate_requested = True
        device.save(update_fields=['deactivate_requested', 'last_seen_at'])
        
        # Step 7: Create audit history entry
        reason = serializer.validated_data.get('reason', 'Admin requested Device Owner deactivation')
        device.create_history_entry(
            action="deactivation_requested",
            user=request.user,
            request=request,
            notes=f"Device Owner deactivation requested. Reason: {reason}",
            changed_fields=["deactivate_requested"],
            old_values={"deactivate_requested": False},
            new_values={"deactivate_requested": True}
        )
        
        logger.info(f"Device {device_id} deactivation requested by {request.user.username}. Reason: {reason}")
        
        # Step 8: Return success response
        return Response({
            "success": True,
            "message": "Device Owner deactivation requested successfully. Device will receive command on next heartbeat.",
            "device_id": device.device_id,
            "deactivate_requested": device.deactivate_requested,
            "requested_at": timezone.now()
        }, status=status.HTTP_200_OK)


@extend_schema(
    tags=["Device Management"],
    summary="Confirm Device Owner deactivation",
    description="Device endpoint to confirm successful Device Owner deactivation. Called by device agent after removing Device Owner.",
    request=DeviceDeactivationConfirmSerializer,
    responses={
        200: DeviceDeactivationConfirmResponseSerializer,
        404: OpenApiResponse(description="Device not found"),
        400: OpenApiResponse(description="Invalid confirmation data")
    },
    examples=[
        OpenApiExample(
            "Success confirmation",
            value={
                "status": "success",
                "message": "Device Owner successfully removed and all restrictions cleared"
            },
            request_only=True
        ),
        OpenApiExample(
            "Failure confirmation",
            value={
                "status": "failed",
                "message": "Device Owner removal failed: Permission denied"
            },
            request_only=True
        ),
        OpenApiExample(
            "Success response",
            value={
                "success": True,
                "message": "Device Owner deactivation confirmed successfully",
                "device_id": "ANDROID-123",
                "deactivated_at": "2026-02-03T10:00:15Z"
            },
            response_only=True
        )
    ]
)
class DeviceDeactivationConfirmView(APIView):
    """Device endpoint to confirm successful deactivation"""
    permission_classes = [AllowAny]
    
    def post(self, request, device_id):
        # Step 1: Validate device API key
        deny = _require_device_api_key(request)
        if deny:
            return deny
        
        # Step 2: Get device
        try:
            device = Device.objects.get(device_id=device_id)
        except Device.DoesNotExist:
            logger.warning(f"Deactivation confirmation for non-existent device: {device_id}")
            return Response({
                "success": False,
                "error": f"Device '{device_id}' not found"
            }, status=status.HTTP_404_NOT_FOUND)
        
        # Step 3: Validate request data
        serializer = DeviceDeactivationConfirmSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        
        # Step 4: Extract confirmation status
        confirmation_status = serializer.validated_data.get('status')
        confirmation_message = serializer.validated_data.get('message', '')
        
        # Step 5: Handle success
        if confirmation_status == 'success':
            logger.info(f"Device {device_id} deactivation confirmed successfully")
            
            # Clear the request flag
            device.deactivate_requested = False
            
            # Record completion timestamp
            device.deactivated_at = timezone.now()
            
            # Save to database
            device.save(update_fields=['deactivate_requested', 'deactivated_at', 'last_seen_at'])
            
            # Create audit history entry
            device.create_history_entry(
                action="deactivation_confirmed",
                request=request,
                notes=f"Device Owner deactivation confirmed successfully. Message: {confirmation_message}",
                changed_fields=["deactivate_requested", "deactivated_at"],
                old_values={
                    "deactivate_requested": True,
                    "deactivated_at": None
                },
                new_values={
                    "deactivate_requested": False,
                    "deactivated_at": device.deactivated_at.isoformat()
                }
            )
            
            logger.info(f"Device {device_id} is now fully deactivated and free from management")
            
            # Return success response
            return Response({
                "success": True,
                "message": "Device Owner deactivation confirmed successfully. Device is now free from management.",
                "device_id": device.device_id,
                "deactivated_at": device.deactivated_at
            }, status=status.HTTP_200_OK)
        
        # Step 6: Handle failure
        else:
            logger.warning(f"Device {device_id} deactivation failed: {confirmation_message}")
            
            # Keep deactivate_requested = True so device will retry
            # Create audit history entry for failure
            device.create_history_entry(
                action="deactivation_failed",
                request=request,
                notes=f"Device Owner deactivation failed. Status: {confirmation_status}, Message: {confirmation_message}",
                changed_fields=["deactivation_status"],
                old_values={"deactivation_status": "in_progress"},
                new_values={"deactivation_status": "failed"}
            )
            
            logger.warning(f"Device {device_id} will retry deactivation on next heartbeat")
            
            # Return error response
            return Response({
                "success": False,
                "message": f"Device Owner deactivation failed: {confirmation_message}",
                "device_id": device.device_id,
                "will_retry": True
            }, status=status.HTTP_400_BAD_REQUEST)


@extend_schema(
    tags=["Device Management"],
    responses={
        200: DeviceListSerializer,
        404: OpenApiResponse(description="Device not found"),
        403: OpenApiResponse(description="Permission denied")
    },
    description="Retrieve, update, or delete a specific device. Only company admins and superusers can delete devices."
)
class DeviceDetailView(RetrieveUpdateDestroyAPIView):
    """
    Device detail view that supports GET, PUT, PATCH, and DELETE operations.
    
    DELETE operation:
    - Only available to superusers and company admins
    - Safely handles cascade deletions of related records
    - Creates audit trail before deletion
    """
    queryset = Device.objects.select_related(
        "shop",
        "company", 
        "registered_by",
        "borrowed_by",
        "management",
        "device_category",
    ).all()
    serializer_class = DeviceListSerializer
    permission_classes = [IsAuthenticated]
    lookup_field = 'device_id'
    lookup_url_kwarg = 'device_id'
    
    def get_queryset(self):
        """Filter devices based on user permissions"""
        user = self.request.user
        qs = super().get_queryset()
        
        # Superusers can see all devices
        if user.is_superuser:
            return qs
            
        # Company admins can see devices from their company
        if hasattr(user, 'company') and user.company:
            return qs.filter(company=user.company)
            
        # Shop owners can see devices from their shops
        if hasattr(user, 'is_shop_owner') and user.is_shop_owner:
            from shops.models import Shop
            user_shops = Shop.objects.filter(user=user)
            return qs.filter(shop__in=user_shops)
            
        # Default: no devices visible
        return qs.none()
    
    def perform_destroy(self, instance):
        """
        Custom delete logic that handles cascade deletions safely
        and creates audit trail
        """
        user = self.request.user
        device_id = instance.device_id
        
        # Permission check: Only superusers and company admins can delete
        if not (user.is_superuser or 
                (hasattr(user, 'is_company_admin') and user.is_company_admin)):
            from rest_framework.exceptions import PermissionDenied
            raise PermissionDenied("Only superusers and company admins can delete devices.")
        
        # Create final audit history entry before deletion
        try:
            instance.create_history_entry(
                action="device_deleted",
                request=self.request,
                notes=f"Device deleted by {user.username}. All related records will be cascade deleted.",
                changed_fields=["deleted"],
                old_values={"status": "active"},
                new_values={"status": "deleted"}
            )
        except Exception as e:
            logger.warning(f"Could not create history entry before deleting device {device_id}: {e}")
        
        # Log the deletion
        logger.info(f"Device {device_id} is being deleted by user {user.username}")
        
        # Manually handle cascade deletions in the same order as Django admin
        try:
            # Delete related records first (same as Django admin logic)
            DeviceHistory.objects.filter(device=instance).delete()
            DeviceTemperSignal.objects.filter(device=instance).delete() 
            DeviceManagement.objects.filter(device=instance).delete()
            DeviceHeartbeatHistory.objects.filter(device=instance).delete()
            
            # Finally delete the device itself
            super().perform_destroy(instance)
            
            logger.info(f"Device {device_id} and all related records deleted successfully")
            
        except Exception as e:
            logger.error(f"Error deleting device {device_id}: {e}")
            raise





# ============================================================
# DEVICE INSTALLATION STATUS ENDPOINTS (NEW)
# ============================================================

class DesktopInstallationStatusView(APIView):
    """Desktop device installation status endpoint"""
    permission_classes = [AllowAny]
    
    @extend_schema(
        tags=["Device Management"],
        summary="Desktop installation status",
        description="Device agent sends installation status with recovery keys for desktop devices. Returns next payment info and server time.",
        parameters=[DEVICE_API_HEADER_PARAM],
        request=OpenApiTypes.OBJECT,
        responses={
            200: OpenApiResponse(
                description="Installation status received with payment info and server time",
                examples=[
                    OpenApiExample(
                        "Response with next payment",
                        value={
                            "device_id": "DEV-FD01FFF056BF",
                            "next_payment": {
                                "server_time": "2026-02-15T19:34:07.427615+03:00",
                                "date_time": "2026-03-15T23:59:00+03:00",
                                "unlocking_password": "249824"
                            }
                        }
                    ),
                    OpenApiExample(
                        "Response without next payment (no active loan)",
                        value={
                            "device_id": "DEV-FD01FFF056BF"
                        }
                    )
                ]
            ),
            400: OpenApiResponse(description="Invalid data"),
            401: OpenApiResponse(description="Missing API key"),
            404: OpenApiResponse(description="Device not found"),
        },
        examples=[
            OpenApiExample(
                "Desktop Installation Success",
                value={
                    "recovery_keys": {
                        "C": "123456-789012-345678-901234-567890-123456",
                        "D": "234567-890123-456789-012345-678901-234567"
                    },
                    "installation_status": {
                        "completed": True,
                        "reason": "Installation successful"
                    }
                },
                request_only=True
            ),
            OpenApiExample(
                "Desktop Installation Failed",
                value={
                    "recovery_keys": {},
                    "installation_status": {
                        "completed": False,
                        "reason": "Failed to enable BitLocker on drive D"
                    }
                },
                request_only=True
            )
        ]
    )
    def post(self, request, device_id):
        """Receive installation status from desktop device agent via POST"""
        # Validate API key
        deny = _require_device_api_key(request)
        if deny:
            return deny
        
        # Get device
        device = get_object_or_404(Device, device_id=device_id)
        
        # Validate device type
        if device.device_type not in ['laptop', 'desktop']:
            return Response({
                "success": False,
                "error": "This endpoint is only for desktop/laptop devices",
                "device_type": device.device_type
            }, status=status.HTTP_400_BAD_REQUEST)
        
        # Get request body data
        recovery_keys = request.data.get('recovery_keys')
        installation_status = request.data.get('installation_status')
        
        # recovery_keys field must be present (can be empty dict if installation failed)
        if recovery_keys is None:
            return Response({
                "success": False,
                "error": "recovery_keys field is required"
            }, status=status.HTTP_400_BAD_REQUEST)
        
        if not installation_status:
            return Response({
                "success": False,
                "error": "installation_status field is required"
            }, status=status.HTTP_400_BAD_REQUEST)
        
        # Validate installation_status structure
        if not isinstance(installation_status, dict):
            return Response({
                "success": False,
                "error": "installation_status must be a dictionary"
            }, status=status.HTTP_400_BAD_REQUEST)
        
        completed = installation_status.get('completed', False)
        reason = installation_status.get('reason', '')
        
        if 'completed' not in installation_status:
            return Response({
                "success": False,
                "error": "installation_status must contain 'completed' field"
            }, status=status.HTTP_400_BAD_REQUEST)
        
        if 'reason' not in installation_status:
            return Response({
                "success": False,
                "error": "installation_status must contain 'reason' field"
            }, status=status.HTTP_400_BAD_REQUEST)
        
        # Update device installation status
        device.installation_completed = completed
        if completed:
            device.installation_completed_at = timezone.now()
        
        # Store recovery keys in disk_recovery_key field (JSONField accepts dict directly)
        device.disk_recovery_key = recovery_keys
        
        device.save(update_fields=['installation_completed', 'installation_completed_at', 'disk_recovery_key'])
        
        # AUTO-ACTIVATE LOAN if installation completed successfully
        loan_activated = False
        loan_activation_error = None
        
        if completed:
            from loans.models import Loan
            from django.contrib.auth import get_user_model
            User = get_user_model()
            
            try:
                # Find approved loan for this device
                loan = Loan.objects.filter(
                    device=device,
                    status='approved'
                ).first()
                
                if loan:
                    # Create system user for activation (or use existing)
                    system_user, _ = User.objects.get_or_create(
                        username='system_agent',
                        defaults={
                            'first_name': 'System',
                            'last_name': 'Agent',
                            'is_active': True
                        }
                    )
                    
                    # Activate the loan
                    loan.activate_loan(activated_by=system_user)
                    loan_activated = True
                    
                    logger.info(f"Loan {loan.loan_number} auto-activated after device {device_id} installation completed")
                    
            except Exception as e:
                loan_activation_error = str(e)
                logger.error(f"Failed to auto-activate loan for device {device_id}: {str(e)}")
        
        # Create history entry
        device.create_history_entry(
            action="info_update",
            request=request,
            notes=f"Desktop installation status: {'completed' if completed else 'failed'}. Reason: {reason}" + 
                  (f". Loan auto-activated: {loan_activated}" if completed else ""),
            changed_fields=["installation_completed", "installation_completed_at", "disk_recovery_key"],
            old_values={
                "installation_completed": False,
                "disk_recovery_key": None
            },
            new_values={
                "installation_completed": completed,
                "installation_completed_at": str(device.installation_completed_at) if completed else None,
                "disk_recovery_key": device.disk_recovery_key
            }
        )
        
        logger.info(f"Desktop device {device_id} installation status: {completed}. Reason: {reason}")
        
        # Get next payment information
        next_payment = _get_next_payment_info(device)
        
        # Build response
        response_data = {
            "device_id": device.device_id
        }
        
        # Add next_payment object if available
        if next_payment:
            # Add server_time to next_payment object
            next_payment["server_time"] = timezone.localtime(timezone.now()).isoformat()
            response_data["next_payment"] = next_payment
        
        return Response(response_data, status=status.HTTP_200_OK)


class MobileInstallationStatusView(APIView):
    """Mobile device installation status endpoint"""
    permission_classes = [AllowAny]
    
    @extend_schema(
        tags=["Device Management"],
        summary="Mobile installation status",
        description="Device agent sends installation status for mobile devices",
        parameters=[DEVICE_API_HEADER_PARAM],
        request=OpenApiTypes.OBJECT,
        responses={
            200: OpenApiResponse(description="Installation status received"),
            400: OpenApiResponse(description="Invalid data"),
            401: OpenApiResponse(description="Missing API key"),
            404: OpenApiResponse(description="Device not found"),
        },
        examples=[
            OpenApiExample(
                "Mobile Installation Success",
                value={
                    "completed": True,
                    "reason": "Device Owner activated successfully"
                },
                request_only=True
            ),
            OpenApiExample(
                "Mobile Installation Failed",
                value={
                    "completed": False,
                    "reason": "User denied Device Owner activation"
                },
                request_only=True
            )
        ]
    )
    def post(self, request, device_id):
        """Receive installation status from mobile device agent via POST"""
        # Validate API key
        deny = _require_device_api_key(request)
        if deny:
            return deny
        
        # Get device
        device = get_object_or_404(Device, device_id=device_id)
        
        # Validate device type
        if device.device_type not in ['phone', 'tablet']:
            return Response({
                "success": False,
                "error": "This endpoint is only for mobile devices (phone/tablet)",
                "device_type": device.device_type
            }, status=status.HTTP_400_BAD_REQUEST)
        
        # Get request body data
        # Support both formats:
        # 1. Direct fields: {"completed": true, "reason": "..."}
        # 2. Wrapped: {"installation_status": {"completed": true, "reason": "..."}}
        
        installation_status = request.data.get('installation_status')
        
        # If installation_status is not provided, check for direct fields
        if not installation_status:
            # Check if completed and reason are provided directly
            if 'completed' in request.data and 'reason' in request.data:
                completed = request.data.get('completed', False)
                reason = request.data.get('reason', '')
            else:
                return Response({
                    "success": False,
                    "error": "Either 'installation_status' object or 'completed' and 'reason' fields are required"
                }, status=status.HTTP_400_BAD_REQUEST)
        else:
            # Validate installation_status structure
            if not isinstance(installation_status, dict):
                return Response({
                    "success": False,
                    "error": "installation_status must be a dictionary"
                }, status=status.HTTP_400_BAD_REQUEST)
            
            completed = installation_status.get('completed', False)
            reason = installation_status.get('reason', '')
            
            if 'completed' not in installation_status:
                return Response({
                    "success": False,
                    "error": "installation_status must contain 'completed' field"
                }, status=status.HTTP_400_BAD_REQUEST)
            
            if 'reason' not in installation_status:
                return Response({
                    "success": False,
                    "error": "installation_status must contain 'reason' field"
                }, status=status.HTTP_400_BAD_REQUEST)
        
        # Update device installation status
        device.installation_completed = completed
        if completed:
            device.installation_completed_at = timezone.now()
        
        device.save(update_fields=['installation_completed', 'installation_completed_at'])
        
        # Create history entry
        device.create_history_entry(
            action="info_update",
            request=request,
            notes=f"Mobile installation status: {'completed' if completed else 'failed'}. Reason: {reason}",
            changed_fields=["installation_completed", "installation_completed_at"],
            old_values={
                "installation_completed": False
            },
            new_values={
                "installation_completed": completed,
                "installation_completed_at": str(device.installation_completed_at) if completed else None
            }
        )
        
        logger.info(f"Mobile device {device_id} installation status: {completed}. Reason: {reason}")
        
        # Return full response for mobile (keep original format)
        return Response({
            "success": True,
            "message": "Installation status received",
            "device_id": device.device_id,
            "installation_completed": completed,
            "reason": reason
        }, status=status.HTTP_200_OK)
