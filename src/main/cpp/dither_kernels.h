#pragma once

#include "dither_types.h"

// ── Ядра диффузии ошибки ───────────────────────────────────────────────────
//
// Ядра хранятся как static const — инициализируются один раз,
// не аллоцируют память при каждом вызове.

static const ErrorKernel KERNEL_FLOYD_STEINBERG[] = {
	{ 1, 0, 7.0 / 16.0},
	{-1, 1, 3.0 / 16.0},
	{ 0, 1, 5.0 / 16.0},
	{ 1, 1, 1.0 / 16.0}
};

static const ErrorKernel KERNEL_STUCKI[] = {
	{ 1, 0, 8.0 / 42.0},
	{ 2, 0, 4.0 / 42.0},
	{-2, 1, 2.0 / 42.0},
	{-1, 1, 4.0 / 42.0},
	{ 0, 1, 8.0 / 42.0},
	{ 1, 1, 4.0 / 42.0},
	{ 2, 1, 2.0 / 42.0},
	{-2, 2, 1.0 / 42.0},
	{-1, 2, 2.0 / 42.0},
	{ 0, 2, 4.0 / 42.0},
	{ 1, 2, 2.0 / 42.0},
	{ 2, 2, 1.0 / 42.0}
};

static const ErrorKernel KERNEL_JJN[] = {
	{ 1, 0, 7.0 / 48.0},
	{ 2, 0, 5.0 / 48.0},
	{-2, 1, 3.0 / 48.0},
	{-1, 1, 5.0 / 48.0},
	{ 0, 1, 7.0 / 48.0},
	{ 1, 1, 5.0 / 48.0},
	{ 2, 1, 3.0 / 48.0},
	{-2, 2, 1.0 / 48.0},
	{-1, 2, 3.0 / 48.0},
	{ 0, 2, 5.0 / 48.0},
	{ 1, 2, 3.0 / 48.0},
	{ 2, 2, 1.0 / 48.0}
};

static const ErrorKernel KERNEL_BURKES[] = {
	{ 1, 0, 8.0 / 32.0},
	{ 2, 0, 4.0 / 32.0},
	{-2, 1, 2.0 / 32.0},
	{-1, 1, 4.0 / 32.0},
	{ 0, 1, 8.0 / 32.0},
	{ 1, 1, 4.0 / 32.0},
	{ 2, 1, 2.0 / 32.0}
};

static const ErrorKernel KERNEL_SIERRA3[] = {
	{ 1, 0, 5.0 / 32.0},
	{ 2, 0, 3.0 / 32.0},
	{-2, 1, 2.0 / 32.0},
	{-1, 1, 4.0 / 32.0},
	{ 0, 1, 5.0 / 32.0},
	{ 1, 1, 4.0 / 32.0},
	{ 2, 1, 2.0 / 32.0},
	{-1, 2, 2.0 / 32.0},
	{ 0, 2, 3.0 / 32.0},
	{ 1, 2, 2.0 / 32.0}
};

static const ErrorKernel KERNEL_SIERRA_LITE[] = {
	{ 1, 0, 2.0 / 4.0},
	{-1, 1, 1.0 / 4.0},
	{ 0, 1, 1.0 / 4.0}
};

static const ErrorKernel KERNEL_ATKINSON[] = {
	{ 1, 0, 1.0 / 8.0},
	{ 2, 0, 1.0 / 8.0},
	{-1, 1, 1.0 / 8.0},
	{ 0, 1, 1.0 / 8.0},
	{ 1, 1, 1.0 / 8.0},
	{ 0, 2, 1.0 / 8.0}
};

// Sierra Two-Row — компромисс между Sierra3 и Sierra Lite
static const ErrorKernel KERNEL_SIERRA2[] = {
	{ 1, 0, 4.0 / 16.0},
	{ 2, 0, 3.0 / 16.0},
	{-2, 1, 1.0 / 16.0},
	{-1, 1, 2.0 / 16.0},
	{ 0, 1, 3.0 / 16.0},
	{ 1, 1, 2.0 / 16.0},
	{ 2, 1, 1.0 / 16.0}
};

// Filter Lite — минимальный однострочный фильтр
static const ErrorKernel KERNEL_FILTER_LITE[] = {
	{ 1, 0, 3.0 / 8.0},
	{-1, 1, 3.0 / 8.0},
	{ 0, 1, 2.0 / 8.0}
};

// Floyd-Steinberg с делителем /20 — мягче стандартного, меньше артефактов на градиентах
static const ErrorKernel KERNEL_FLOYD_STEINBERG_20[] = {
	{ 1, 0, 7.0 / 20.0},
	{-1, 1, 3.0 / 20.0},
	{ 0, 1, 5.0 / 20.0},
	{ 1, 1, 1.0 / 20.0}
};

// Floyd-Steinberg с делителем /24 — ещё мягче, минимальный дизеринг
static const ErrorKernel KERNEL_FLOYD_STEINBERG_24[] = {
	{ 1, 0, 7.0 / 24.0},
	{-1, 1, 3.0 / 24.0},
	{ 0, 1, 5.0 / 24.0},
	{ 1, 1, 1.0 / 24.0}
};

// Fan — однострочный фильтр с обратным распределением
static const ErrorKernel KERNEL_FAN[] = {
	{ 1, 0, 7.0 / 16.0},
	{-2, 1, 1.0 / 16.0},
	{-1, 1, 3.0 / 16.0},
	{ 0, 1, 5.0 / 16.0}
};

// Shiau-Fan — компактный однострочный фильтр
static const ErrorKernel KERNEL_SHIAU_FAN[] = {
	{ 1, 0, 4.0 / 8.0},
	{-2, 1, 1.0 / 8.0},
	{-1, 1, 1.0 / 8.0},
	{ 0, 1, 2.0 / 8.0}
};

// Shiau-Fan 2 — расширенный вариант с двумя строками
static const ErrorKernel KERNEL_SHIAU_FAN_2[] = {
	{ 1, 0, 8.0 / 16.0},
	{-3, 1, 1.0 / 16.0},
	{-2, 1, 1.0 / 16.0},
	{-1, 1, 2.0 / 16.0},
	{ 0, 1, 4.0 / 16.0}
};

// Pigeon — однострочный фильтр с симметричным распределением назад
static const ErrorKernel KERNEL_PIGEON[] = {
	{ 1, 0, 7.0 / 16.0},
	{-1, 1, 1.0 / 16.0},
	{ 0, 1, 5.0 / 16.0},
	{ 1, 1, 3.0 / 16.0}
};

// Nakano — трёхстрочный алгоритм с широким распределением ошибки
static const ErrorKernel KERNEL_NAKANO[] = {
	{ 1, 0, 7.0 / 48.0},
	{ 2, 0, 5.0 / 48.0},
	{-2, 1, 2.0 / 48.0},
	{-1, 1, 4.0 / 48.0},
	{ 0, 1, 8.0 / 48.0},
	{ 1, 1, 4.0 / 48.0},
	{ 2, 1, 2.0 / 48.0},
	{-2, 2, 1.0 / 48.0},
	{-1, 2, 2.0 / 48.0},
	{ 0, 2, 6.0 / 48.0},
	{ 1, 2, 4.0 / 48.0},
	{ 2, 2, 3.0 / 48.0}
};

// Zhou-Fang — двухстрочный алгоритм с акцентом на диагональные соседи
static const ErrorKernel KERNEL_ZHOU_FANG[] = {
	{ 1, 0, 8.0 / 32.0},
	{ 2, 0, 2.0 / 32.0},
	{-2, 1, 1.0 / 32.0},
	{-1, 1, 4.0 / 32.0},
	{ 0, 1, 8.0 / 32.0},
	{ 1, 1, 6.0 / 32.0},
	{ 2, 1, 3.0 / 32.0}
};

static inline KernelView get_kernel(DitherAlgorithm algorithm) {
	switch (algorithm) {
		case FLOYD_STEINBERG:    return {KERNEL_FLOYD_STEINBERG, 4};
		case STUCKI:             return {KERNEL_STUCKI, 12};
		case JJN:                return {KERNEL_JJN, 12};
		case BURKES:             return {KERNEL_BURKES, 7};
		case SIERRA3:            return {KERNEL_SIERRA3, 10};
		case SIERRA_LITE:        return {KERNEL_SIERRA_LITE, 3};
		case ATKINSON:           return {KERNEL_ATKINSON, 6};
		case SIERRA2:            return {KERNEL_SIERRA2, 7};
		case FILTER_LITE:        return {KERNEL_FILTER_LITE, 3};
		case FLOYD_STEINBERG_20: return {KERNEL_FLOYD_STEINBERG_20, 4};
		case FLOYD_STEINBERG_24: return {KERNEL_FLOYD_STEINBERG_24, 4};
		case FAN:                return {KERNEL_FAN, 4};
		case SHIAU_FAN:          return {KERNEL_SHIAU_FAN, 4};
		case SHIAU_FAN_2:        return {KERNEL_SHIAU_FAN_2, 5};
		case PIGEON:             return {KERNEL_PIGEON, 4};
		case NAKANO:             return {KERNEL_NAKANO, 12};
		case ZHOU_FANG:          return {KERNEL_ZHOU_FANG, 7};
		default:                 return {KERNEL_FLOYD_STEINBERG, 4};
	}
}
