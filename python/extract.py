import argparse

import cv2
import numpy as np

TARGET_HEIGHT = 96


def dist(p0, p1):
    return np.sqrt((p0[0] - p1[0]) ** 2 + (p0[1] - p1[1]) ** 2)


def angle_points(p0, p1):
    dx = p0[0] - p1[0]
    dy = p0[1] - p1[1]
    return np.arctan2(dx, dy) % (np.pi / 2)


def find_angle(contour):
    angles = []
    for i in range(1, len(contour)):
        if dist(contour[i][0], contour[i - 1][0]) > 100:
            angles.append(angle_points(contour[i][0], contour[i - 1][0]))
    angle = np.mean(angles) * 180 / np.pi
    std_angle = np.std(angles) * 180 / np.pi
    if std_angle > 1:
        filtered_angles = []
        for a in angles:
            if a > 1.4:
                filtered_angles.append(a - np.pi / 2)
            else:
                filtered_angles.append(a)
        angle = np.mean(filtered_angles) * 180 / np.pi
        std_angle = np.std(filtered_angles) * 180 / np.pi
        if std_angle > 1:
            raise Exception("Unable to compute angle")
    return angle, std_angle


def center(contour):
    moment = cv2.moments(contour)
    return moment['m10'] / moment['m00'], moment['m01'] / moment['m00']


def cart2pol(x, y):
    theta = np.arctan2(y, x)
    rho = np.hypot(x, y)
    return theta, rho


def pol2cart(theta, rho):
    x = rho * np.cos(theta)
    y = rho * np.sin(theta)
    return x, y


def expand(contour, x_scale, y_scale, angle):
    ctr = center(contour)
    cnt_norm = contour - ctr

    coordinates = cnt_norm[:, 0, :]
    xs, ys = coordinates[:, 0], coordinates[:, 1]
    thetas, rhos = cart2pol(xs, ys)

    thetas_deg = np.rad2deg(thetas)
    thetas_new_deg = (thetas_deg + angle) % 360
    thetas_new = np.deg2rad(thetas_new_deg)

    xs, ys = pol2cart(thetas_new, rhos)
    xs *= x_scale
    ys *= y_scale

    cnt_norm[:, 0, 0] = xs
    cnt_norm[:, 0, 1] = ys

    cnt_final = cnt_norm + ctr
    return cnt_final.astype(np.int32)


def main():
    parser = argparse.ArgumentParser(description="Card extractor")
    parser.add_argument("image", help="scanned cards image")
    args = parser.parse_args()

    original = cv2.imread(args.image)

    gray = cv2.cvtColor(original, cv2.COLOR_BGR2GRAY)

    threshold, thresh_img = cv2.threshold(gray, 180, 255, cv2.THRESH_BINARY)
    thresh_img = 255 - thresh_img

    contours, h = cv2.findContours(thresh_img, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
    print(f"Found {len(contours)} contours")

    cnt_image = original.copy()

    card_contours = []

    parent = -1
    for idx, cnt in enumerate(contours):
        if h[0][idx][3] < 1:
            if cv2.contourArea(cnt) > 800_000:
                parent = idx
        if h[0][idx][3] != parent:
            continue
        approx = cv2.approxPolyDP(cnt, 0.005 * cv2.arcLength(cnt, True), True)
        if cv2.contourArea(cnt) < 100_000:
            continue
        if cv2.contourArea(cnt) > 800_000:
            cv2.drawContours(cnt_image, [cnt], 0, (0, 0, 255), 3)
            print(f"discarding large contour : {idx}")
            continue
        if len(approx) < 10:
            cv2.drawContours(cnt_image, [cnt], 0, (0, 255, 0), 3)
            card_contours.append(cnt)

    print(f"Found {len(card_contours)} cards")

    bgra = cv2.cvtColor(original, cv2.COLOR_BGR2BGRA)

    alpha = np.ones(bgra.shape[:2], dtype=bgra.dtype)

    for contour in card_contours:
        cv2.drawContours(alpha, [contour], 0, 255, cv2.FILLED)

    eroded = cv2.erode(alpha, None) // 255

    bgra = cv2.bitwise_and(bgra, bgra, mask=eroded)

    for idx, contour in enumerate(card_contours):
        approx = cv2.approxPolyDP(contour, 2, True)
        angle, s = find_angle(approx)

        print(f"Angle: {angle:.2f}")
        if angle < 35:
            angle += 90

        rotated_cnt = expand(contour, 1, 1, angle - 90)

        rot = cv2.getRotationMatrix2D(center(approx), 90 - angle, 1)
        rotated = cv2.warpAffine(bgra, rot, (bgra.shape[1], bgra.shape[0]))

        bbox = cv2.boundingRect(rotated_cnt)
        cropped = rotated[bbox[1]:(bbox[1] + bbox[3]), bbox[0]:bbox[0] + bbox[2], :]

        try:
            cv2.imwrite(f"full_{idx}.png", cropped)
        except Exception:
            print(f"image {idx} failed")
            continue

        scale = TARGET_HEIGHT / cropped.shape[0]
        resized = cv2.resize(cropped, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
        cv2.imwrite(f"card_{idx}.png", resized)


if __name__ == "__main__":
    main()
